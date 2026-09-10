# MTA transport

Texto submits already-composed RFC 822 messages to an SMTP MTA through `MtaClient`.

```
EmailService → Outbox → RabbitMQ → EmailDeliveryWorker → EmailDeliveryProcessor
    → SmtpDeliveryEngine (MIME compose + DKIM sign)
        → MtaClient
            ├── MailpitMtaClient
            └── PostfixMtaClient
```

The application owns tenant authorization, verified senders, suppression, quotas, MIME composition, DKIM signing, and delivery state. The MTA owns the SMTP conversation after handoff, queueing, and (in a future production environment) downstream delivery.

## Implementations

| `email-platform.mta.implementation` | Client | Role |
| --- | --- | --- |
| `mailpit` | `MailpitMtaClient` | Local mailbox / development sink |
| `postfix` | `PostfixMtaClient` | Production-oriented MTA client |
| any other value | — | Startup failure |

Selection is configuration/DI only. The application does not switch implementations based on `spring.profiles.active`.

### Mailpit

Default for local development and existing tests.

Mailpit is a mailbox inspection sink. It is not a production MTA.

### Postfix

Postfix is the intended production MTA implementation.

**This step integrates Postfix only for controlled local testing.** Public Internet delivery is not enabled. The Compose/test Postfix instance:

- accepts SMTP submission from the application
- delivers only to the local test domain `texto.test` (for example `alice@texto.test`)
- rejects unauthorized external destinations (`reject_unauth_destination` is evaluated before any mynetworks permit, so local clients cannot relay to the public Internet)
- sets `default_transport` / `relay_transport` to error so queued mail cannot be delivered to public MX servers
- is published on `127.0.0.1:2525` only

Do not send test mail to Gmail, Outlook, Yahoo, or other real recipients through this environment.

## Configuration

```yaml
email-platform:
  mta:
    implementation: mailpit   # or postfix
    smtp:
      host: localhost
      port: 1025              # Mailpit 1025; local Postfix 2525
      connection-timeout-ms: 5000
      read-timeout-ms: 5000
      write-timeout-ms: 5000
      ehlo-hostname: texto.local
      starttls:
        enabled: false
        required: false
      ssl:
        enabled: false
```

Environment variables override deployment-specific values (`MTA_IMPLEMENTATION`, `MTA_SMTP_HOST`, `MTA_SMTP_PORT`, `MTA_SMTP_STARTTLS_ENABLED`, `MTA_SMTP_STARTTLS_REQUIRED`, `MTA_SMTP_SSL_ENABLED`, timeouts, `MTA_SMTP_EHLO_HOSTNAME`).

Unknown `implementation` values fail at startup. Invalid TLS combinations fail at startup (`starttls.required` without `starttls.enabled`, or STARTTLS and implicit SSL together). Production (`prod` profile) requires `implementation=postfix` and STARTTLS (enabled+required) or implicit SSL. There is no trust-all certificate mode and no silent fallback from required STARTTLS.

## Local TLS policy

For this controlled local environment the application talks to Postfix over **plaintext SMTP** (`starttls.enabled=false`, `ssl.enabled=false`). Local certificates would make STARTTLS brittle; this is not a production default.

Production configuration is separate (`application-prod.yml`): STARTTLS enabled and required unless implicit SSL is explicitly configured. Certificate validation uses the JVM default trust store.

## Local topology

```
Texto application
      ↓
Postfix (127.0.0.1:2525 → container :25)
      ↓
local virtual mailbox for texto.test
```

Mailpit remains available on `127.0.0.1:1025` for mailbox inspection when `implementation=mailpit`.

To use local Postfix from the running app:

```
MTA_IMPLEMENTATION=postfix
MTA_SMTP_HOST=localhost
MTA_SMTP_PORT=2525
MTA_SMTP_STARTTLS_ENABLED=false
MTA_SMTP_STARTTLS_REQUIRED=false
```

Default local behavior stays Mailpit so existing developers are unchanged.

## Future production topology (not enabled)

```
Texto → RabbitMQ → Delivery Worker → Delivery Engine → Postfix → Internet recipient MX
```

Public Internet delivery, production IPs, rDNS/PTR, bounce ingestion, IP pools, and warmup are **not** part of this phase.

## DKIM

The RFC 822 bytes submitted to Postfix already contain `DKIM-Signature`. Postfix must not regenerate DKIM or alter signed headers/body. Application-side DKIM remains authoritative.
