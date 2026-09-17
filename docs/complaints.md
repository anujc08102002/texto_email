# Complaint / feedback-loop ingestion

**Public complaint/feedback-loop ingestion is not enabled.**

A complaint is not a bounce. Bounces are delivery failures. Complaints are recipient or provider reports of unwanted mail and may arrive after a successful MTA handoff.

This step is application foundation plus controlled testing. There is no Gmail, Yahoo, or Outlook feedback-loop connection, no public complaint webhook, and no public Internet delivery.

```
Trusted infrastructure (test / future provider adapter)
          ↓
 TrustedComplaintIngressService     (application bean, not a public API)
          ↓
 RabbitMQ email.complaint
          ↓
 ComplaintWorker → ComplaintIngestionService → ComplaintPolicy
          ↓
 complaint_events + recipient suppression (when policy says so)
```

Complaints are **not** published to `email.bounce` and **do not** call `BouncePolicy`.

## Complaint vs bounce

| | Bounce | Complaint |
| --- | --- | --- |
| Meaning | Delivery failed | Recipient/provider reported unwanted mail |
| Typical timing | SMTP-time or post-acceptance DSN | Often after `DELIVERED` (MTA accepted) |
| Message status | May become `BOUNCED` | Unchanged |
| Suppression type | `BOUNCE` | `COMPLAINT` |
| Policy | `BouncePolicy` | `ComplaintPolicy` |

Do not classify complaints as DSNs. Do not add `BounceClass.COMPLAINT`.

## Correlation

Preferred order:

1. Opaque bounce/message correlation token (`bounce_correlation_token` / `bounce+<token>@bounce-domain`)
2. Unique provider message id
3. Unique RFC 822 Message-ID

Recipient address alone never correlates. Tenant id supplied on an inbound complaint is ignored; tenant comes from the matched outbound row.

A well-formed token that is unknown does **not** fall back to Message-ID (same anti-mix rule as bounce).

Message-ID / provider fallbacks persist `MATCHED` for audit but **do not authorize suppression**. Only token-authoritative correlation applies `ComplaintPolicy`.

Uncorrelated complaints are stored as `UNCORRELATED` with no suppression.

## Idempotency

- Prefer `provider` + `providerEventId` → SHA-256 `event_hash`
- Otherwise canonical hash of provider, message ids, recipient, type, and token
- Unique on `event_hash`
- Partial unique index on `(provider, provider_event_id)` when present
- `received_at` / `occurred_at` are not event ids

Duplicates skip policy and do not create a second suppression row (`SuppressionService` insert-if-absent).

## Recipient suppression

Token-correlated complaint whose recipient is on the outbound message:

- type `COMPLAINT`
- source `SYSTEM`
- reason `COMPLAINT`

Unknown complaint category still suppresses (no product distinction between ABUSE / SPAM / FRAUD / UNKNOWN yet).

Does not overwrite existing `MANUAL`, `BOUNCE`, or `COMPLAINT` rows. Does not unsuppress. Does not suppress the sending domain, tenant, or unrelated recipients.

Alice complaining does not suppress Bob on the same message.

Future sends use the existing `SuppressionService.isSuppressed` check before queueing. There is no second lookup.

## Delivery-state behavior

There is no `COMPLAINT` delivery status. A complaint does **not** transition `DELIVERED` → `FAILED` or `BOUNCED`. Delivery may have succeeded; the complaint affects future eligibility only.

## Tenant isolation

```
raw complaint
  → sanitize / bound
  → extract token or unique ids
  → database lookup
  → tenant + message from the row
  → ComplaintPolicy
```

Tenant B’s token cannot suppress Tenant A’s recipient, even if the payload copies Tenant A’s Message-ID or recipient address.

## Worker / DLQ

`ComplaintWorker` consumes `email.complaint` (DLX `email.complaint.dlx` / DLQ `email.complaint.dlq`).

| Outcome | Ack |
| --- | --- |
| SUCCESS / DUPLICATE / UNCORRELATED / persisted PARSE_FAILED | ack |
| `DataAccessException` / transaction failure | nack **requeue** |
| Other unexpected errors | nack without requeue → DLQ |

Malformed JSON and oversized payloads are persisted as `PARSE_FAILED` and acked (not retried forever). Ack happens only after the ingestion transaction commits.

## Provider-neutral design

Core input is `ComplaintIngestionRequest`: provider, providerEventId, messageId, recipient, correlationToken, type, occurredAt, bounded metadata.

The domain does **not** depend on Gmail/Yahoo/Outlook payload classes. Future provider adapters belong outside this module and must publish through `TrustedComplaintIngressService`.

## Future FBL integrations

Not implemented:

- Gmail FBL
- Yahoo complaint feed
- Outlook complaint feed
- provider API polling
- public complaint HTTP webhook
- public MX

When those adapters exist they should:

1. Normalize into `ComplaintIngestionRequest`
2. Call trusted ingress (not a customer API)
3. Never treat a provider-supplied tenant id as authoritative

## Webhooks

If a tenant webhook subscribes to `email.complaint`, the existing webhook publisher emits that event after suppression. Webhook failure is caught and does **not** roll back the complaint event or suppression. Customer notification is downstream of the internal source-of-truth row.

## Observability

Counters: `complaint_events_total`, `complaints_correlated_total`, `complaints_uncorrelated_total`, `complaint_suppressions_total`, `duplicate_complaints_total`.

Logs include message id, tenant id, type, token fingerprint. They do not include full payloads, bounce tokens, or complete recipient addresses.

**Public complaint/feedback-loop ingestion is not enabled.**
