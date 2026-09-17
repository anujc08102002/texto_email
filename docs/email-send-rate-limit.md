# Email send rate limit

`email-platform.email.rate-limit-per-minute` (`EMAIL_RATE_LIMIT_PER_MINUTE`, default `120`) is the maximum number of **email messages** a tenant may **accept** for outbound processing during a UTC minute.

It is an application acceptance limit. It is not monthly quota, Postfix connection limiting, per-destination throttling, IP warmup, or HTTP API-key request limiting.

## Semantic

| Concept | Unit | Scope |
| --- | --- | --- |
| Send rate limit | 1 accepted **message** | Per tenant, per UTC minute |
| Monthly quota | 1 **deliverable recipient** | Per tenant, per billing period |

One API request that persists as a queued message consumes **one** send-rate unit even when it has multiple recipients. Fully suppressed messages are not accepted for outbound processing and do not consume a send-rate unit.

## Tenant scope

Identity comes from authenticated `TenantContext`, never from a client-supplied tenant id.

Redis key: `rate-limit:<tenant-id>:email-send:<utc-epoch-minute>`.

Tenant A exhausting its window does not affect Tenant B.

## Enforcement point

`EmailService.sendInternal`, after entitlement, validation, verified sender, suppression split, and idempotency replay, **before** monthly quota, persist, and outbox.

Delivery workers, RabbitMQ redelivery, Postfix retries, and webhooks do not consume another unit.

## Algorithm

Atomic Redis Lua script: read current count, reject without increment when `current >= limit`, otherwise `INCR` and `EXPIRE` in the same script.

Fixed window aligned to the UTC minute. At the minute boundary a new counter starts, so a burst of up to `2 × limit` is possible across the last second of one minute and the first second of the next.

## Exceeded

- HTTP `429`
- code `EMAIL_RATE_LIMIT_EXCEEDED`
- message does not include Redis keys, tenant ids, or infrastructure details
- `Retry-After` is seconds remaining in the current UTC minute window

The message is not persisted as accepted and is not published to RabbitMQ.

## Redis outage

Fail-closed in **every** environment, including local and test. If Redis cannot be reached, acceptance returns HTTP `503` / `EMAIL_RATE_LIMIT_UNAVAILABLE` (“Email sending is temporarily unavailable”). An outage must not become an unlimited send path.

Local compose already includes Redis. Do not run the API without Redis and expect sending to work.

Startup rejects `rate-limit-per-minute` outside `1`–`100000`. Production cannot disable the limiter by setting `0`. Missing env uses the default `120`.

## Idempotency

Order: authenticate → validate → **return existing idempotent result** → rate-limit new acceptance → quota → persist → enqueue.

A retry with the same `Idempotency-Key` that already accepted does not consume another send-rate unit. A unique-constraint race after consume refunds the unit.

## Metrics

`email_rate_limit` with tag `result=allowed|rejected|error`. No tenant ids.
