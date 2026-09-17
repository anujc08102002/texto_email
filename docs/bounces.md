# Bounce / DSN ingestion

**Public inbound MX and Internet bounce handling are not enabled.**

Bounce ingestion is not connected to public Internet delivery yet. There is no public MX, no public bounce SMTP, and no production DNS MX.

```
Test recipient / simulated DSN     (local only)
          ↓
       Postfix                      (controlled; bounce.texto.test mailbox)
          ↓
 Trusted DSN ingress                (application bean, not a public API)
          ↓
 RabbitMQ email.bounce
          ↓
 BounceDsnWorker → DsnIngestionService → BouncePolicy
          ↓
 bounce event + recipient state + suppression (when policy says so)
```

**Public Internet bounce handling is not enabled.**

Complaint / feedback-loop ingestion is a separate module (`docs/complaints.md`). **Public complaint/feedback-loop ingestion is not enabled.**

Future production topology (not this step):

Recipient MX → Postfix inbound/DSN → trusted ingress → `email.bounce` → worker → delivery state → suppression policy.

## Bounce correlation token

Each outbound `email_messages` row receives a `bounce_correlation_token` at create time:

- 128-bit `SecureRandom` encoded as 32 lowercase hex characters
- not derived from tenant id, email id, or recipient
- unique globally and within a tenant
- stable for the lifetime of the message (first write wins)

The current delivery model is **one row per message** (JSONB recipient arrays), not per-recipient deliveries. The token is therefore **message-level**. Per-recipient VERP is out of scope.

The token is **not** exposed on customer email APIs or frontend responses.

## Token security

- Guessing a 128-bit token is not a practical attack.
- A token is not a tenant credential. Lookup `findByBounceCorrelationToken` resolves **tenant + message** from the database.
- Inbound DSNs cannot supply `tenantId`.
- Logs use an 8-hex SHA-256 fingerprint, never the raw token or complete bounce address.
- Unknown / malformed tokens produce a generic unmatched/parse failure. No public “token exists” API.

## Envelope MAIL FROM vs visible From

The bounce address is the SMTP envelope sender only:

```
MAIL FROM:<bounce+<token>@bounce.texto.test> ENVID=<token>
RCPT TO:<customer-recipient>
```

The RFC 822 message keeps the customer’s verified identity:

```
From: noreply@customer.example
To: recipient
DKIM-Signature: d=customer.example
```

The application does **not** insert a `Return-Path` header. Receiving MTAs (including local Postfix virtual delivery) create Return-Path from the envelope.

DKIM signs the visible From domain. The envelope is not signed.

## Return-Path format

Configurable platform domain (`email-platform.bounce.domain`, env `BOUNCE_DOMAIN`).

Local default: `bounce.texto.test`

Conceptual production: `bounce.texto.email` (set via environment; not hardcoded).

Address: `bounce+<opaque-token>@<bounce-domain>`

## Correlation priority

1. Opaque bounce token (envelope RCPT TO of the DSN, Original-Envelope-ID / ENVID, original Return-Path, DSN To)
2. Original-Envelope-ID as Message-ID / provider id when no token was extracted
3. RFC 822 Message-ID (compatibility fallback; **not** the preferred identifier)
4. Provider message id (Postfix queue id)

When a well-formed bounce token is present but unknown, Message-ID fallback is **not** used (prevents mixing a guessed/wrong token with a spoofed Message-ID).

Message-ID remaining as a fallback is for messages that never had a bounce envelope. It is weaker than the token and **does not authorize state mutation or suppression**.

## Tenant resolution

```
raw DSN
  → parse
  → extract bounce token
  → database lookup
  → tenant + message from the row
  → persist bounce_events
  → BouncePolicy (token-correlated recipients only)
```

Cross-tenant: Tenant B’s token matching Tenant B’s row cannot mutate Tenant A, even if the DSN copies Tenant A’s Message-ID. Recipient address alone never correlates.

## SMTP-time bounce vs DSN bounce

| Path | When | Handler |
| --- | --- | --- |
| SMTP-time | Postfix/application SMTP returns 5xx (e.g. 550) during submission | `EmailDeliveryProcessor.handlePermanent` → message `BOUNCED`, `recordBounce(..., smtp_550)` |
| Post-acceptance DSN | Postfix already returned 250; later RFC 3464 DSN | `DsnIngestionService` + `BouncePolicy` |

`DELIVERED` still means **Postfix accepted the message** (MTA handoff), not mailbox delivery. A later DSN may transition `DELIVERED → BOUNCED` when every deliverable recipient on that message has a hard bounce.

The application does **not** introduce a separate `MTA_ACCEPTED` status. Post-MTA reconciliation uses the existing `DELIVERED` name plus recipient lists `bounced_recipients` / `soft_bounced_recipients`.

## Hard bounce policy

Token-correlated `HARD_BOUNCE` (except policy rejection):

- Record the affected stored recipient on `bounced_recipients` (original casing preserved)
- Permanent suppression: type `BOUNCE`, source `SYSTEM`, reason `HARD_BOUNCE`
- If **all** deliverable To/Cc/Bcc recipients are in `bounced_recipients`, message status → `BOUNCED` (`SENDING` / `DELIVERED` / `DEFERRED` allowed)
- Do **not** mark the whole message `BOUNCED` when only one of several recipients bounced
- Do not suppress the sending domain, tenant, or unrelated recipients

## Soft bounce policy

Token-correlated `SOFT_BOUNCE`:

- Record the recipient on `soft_bounced_recipients`
- Do **not** permanently suppress
- Do **not** move the message to `DEFERRED` or re-queue SMTP

Post-acceptance, Postfix already owns downstream retry. The application retry/TTL queues remain the authority for **SMTP-time** temporary failures only. Re-submitting after a DSN soft bounce would duplicate the message.

## Unknown policy

`UNKNOWN` (including `Action=delivered/relayed/expanded`): persist the DSN event. `NO_ACTION` — no delivery mutation, no suppression.

## Policy rejection

`HARD_BOUNCE` + failure kind `POLICY_REJECTION` (typically RFC 3463 subject 7):

- Record the recipient bounce and possibly mark the message `BOUNCED` if it was the only deliverable recipient
- Do **not** automatically suppress (rate-limit / reputation / content / recipient-side policy can be temporary)

## Recipient-level behavior

Outbound mail is one `email_messages` row with JSONB recipient arrays. Matching uses `EmailNormalizer` (trim + lowercase) against To/Cc/Bcc. The stored string is not rewritten.

A DSN for `alice@example.com` does not change `bob@example.com`.

## Suppression source/reason

| Origin | type | source | reason |
| --- | --- | --- | --- |
| SMTP-time 550 | `BOUNCE` | `SYSTEM` | `smtp_550` |
| DSN hard bounce | `BOUNCE` | `SYSTEM` | `HARD_BOUNCE` |
| Complaint / FBL | `COMPLAINT` | `SYSTEM` | `COMPLAINT` |
| Manual | `MANUAL` | `USER` | (user) |

Upsert is unique on `(tenant_id, normalized_email)`. An existing MANUAL/COMPLAINT row is left unchanged. Successful later delivery does not unsuppress. DSN-generated suppression is checked by the existing `SuppressionService.isSuppressed` path before queueing.

## Idempotency

SHA-256 `event_hash` uniqueness. Duplicate DSNs skip policy application. `recordBounce` is insert-if-absent.

## Worker retry/DLQ

`BounceDsnWorker` calls `DsnIngestionService` (transactional) then acks.

- Success, unmatched, parse-failed (persisted): ack
- `DataAccessException` / transaction failure: nack **requeue** (retry)
- Other unexpected errors: nack without requeue → `email.bounce.dlq`
- Malformed MIME does not throw; it is persisted as `PARSE_FAILED` and acked

## Trusted inbound boundary

`TrustedDsnIngressService` is infrastructure-only. It is **not** `POST /api/bounces`.

It publishes the raw RFC 822 payload to RabbitMQ `email.bounce` with header `x-envelope-recipient`. `BounceDsnWorker` consumes that queue (DLX `email.bounce.dlx` / DLQ `email.bounce.dlq`).

Optional: `email-platform.bounce.spool-directory` enables `DsnMailboxPoller` against a local Postfix bounce maildir. Unset by default.

## Local Postfix topology

- Virtual mailbox domains: `texto.test` (sink) and configurable `bounce.texto.test` (DSN mailbox under `/var/mail/bounce`)
- Only `bounce+<32hex>@<bounce-domain>` is accepted on the bounce domain
- `smtpd_relay_restrictions = reject_unauth_destination` unchanged (no open relay)
- `default_transport` remains error — no public Internet delivery
- Listener still `127.0.0.1:2525` — not a public MX

**Public Internet bounce handling is not enabled.**
