# Outbound MTA transport

The application never talks to Mailpit or Postfix by name in the delivery worker.
It submits an already composed, DKIM-signed RFC 822 message through `MtaClient`.

## Topology

```
Customer
  → Texto API (acceptance, verified sender, suppression, quota, templates)
  → PostgreSQL outbox
  → RabbitMQ
  → Delivery worker / EmailDeliveryProcessor
  → DeliveryEngine (MIME compose + DKIM sign)
  → MtaClient
      ├── MailpitMtaClient     local/test SMTP sink (current)
      └── PostfixMtaClient     future production relay (not implemented)
  → recipient MX                Postfix responsibility later
```

## Responsibilities

| Layer | Owns |
| --- | --- |
| API / domain | Tenant auth, verified From, suppression, quota, templates |
| DeliveryEngine | MIME composition, DKIM signing, mapping transport results to the existing state machine |
| MtaClient | SMTP `EHLO` / `MAIL FROM` / `RCPT TO` / `DATA`, timeouts, TLS |
| Message state machine | Retry, deferred, bounced, failed |
| Recipient systems | SPF/DKIM/DMARC evaluation |

DKIM is message authentication and happens **before** `MtaClient`. The MTA client must not alter From, To, Subject, Date, Message-ID, body, MIME boundaries, or `DKIM-Signature`.

SMTP envelope (`MAIL FROM` / `RCPT TO`) is separate from MIME headers. Envelope sender is the verified From identity. VERP / bounce mailbox addresses are not implemented yet.

## Local vs production

- **Mailpit** (`email-platform.mta.implementation=mailpit`): local development and automated tests. STARTTLS and implicit SSL are **disabled**. This is not a silent downgrade; production must set TLS explicitly later.
- **Postfix**: future production MTA. Do not set `implementation=postfix` yet — the process fails fast because that client does not exist.

SPF authentication will depend on the production sending IP published behind `_spf.texto.email`. DMARC is evaluated by receiving systems. Bounce and complaint ingestion are a later subsystem.

## TLS

| Setting | Meaning |
| --- | --- |
| `starttls.enabled=false`, `required=false` | TLS disabled (Mailpit default) |
| `starttls.enabled=true`, `required=false` | STARTTLS optional (not used locally) |
| `starttls.enabled=true`, `required=true` | STARTTLS required; no plaintext fallback |
| `ssl.enabled=true` | Implicit TLS (SMTPS) |

When TLS is enabled, server identity is checked (`mail.smtp.ssl.checkserveridentity=true`). There is no trust-all-certificates mode.

## Configuration

Existing `MAILPIT_HOST` / `MAILPIT_SMTP_PORT` remain the local defaults. Optional `MTA_SMTP_*` overrides:

```
MTA_IMPLEMENTATION=mailpit
MTA_REQUIRE_DKIM=true
MTA_STARTTLS_ENABLED=false
MTA_STARTTLS_REQUIRED=false
```

Do not put SMTP passwords in source files. Use environment variables.
