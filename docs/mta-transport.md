# MTA transport

Texto submits already-composed RFC 822 messages to an SMTP MTA through `MtaClient`.

```
Customer
  → Texto API
    → EmailService
      → Outbox
        → RabbitMQ
          → EmailDeliveryWorker
            → EmailDeliveryProcessor
              → SmtpDeliveryEngine (MIME compose + DKIM sign)
                → MtaClient
                    ├── MailpitMtaClient
                    ├── PostfixMtaClient
                    └── SesMtaClient
                      → Postfix
                        → controlled/local delivery (default)
                        → recipient MX (only when dual public-delivery switches are on; not enabled by default)
```

        The application owns tenant authorization, verified senders, suppression, quotas, send-rate limits, MIME composition, DKIM signing, and delivery state. Application send-rate limiting is tenant-scoped and message-based; see [docs/email-send-rate-limit.md](email-send-rate-limit.md). Production identity, DNS/PTR diagnostics, and the dual public-delivery kill switch are documented in [docs/production-smtp.md](production-smtp.md). Internet delivery is not enabled.

The MTA owns the SMTP conversation after handoff, its own queue, and (in a future production environment) downstream MX delivery.

### Application boundary

Texto is an authenticated/trusted SMTP **submission client**. It never speaks to recipient MX servers. `MtaClient.submit` returns success when Postfix accepts the message (RFC 5321 `250` after `DATA`, typically `Ok: queued as <queueid>`).

That is **MTA handoff**, not recipient delivery. Application status `DELIVERED` currently means “Postfix accepted the message”. Bounce/DSN ingestion has a trusted local Postfix bounce-domain path (`docs/bounces.md`). **Public inbound MX and Internet bounce handling are not enabled.**

Do not log message bodies, DKIM private keys, SMTP passwords, or complete MIME.

Correlation:

| Layer | Identifier |
| --- | --- |
| Application | `tenantId`, `messageId` (API / outbox / worker logs) |
| MTA handoff | Postfix queue id stored as `providerMessageId` from `250 … queued as <id>` |
| Postfix logs | same queue id on stdout (`maillog_file = /dev/stdout`) |

## Implementations

| `email-platform.mta.implementation` | Client | Role |
| --- | --- | --- |
| `mailpit` | `MailpitMtaClient` | Local mailbox / development sink |
| `postfix` | `PostfixMtaClient` | Production-oriented MTA client |
| `ses` | `SesMtaClient` | Amazon SESv2 API client using RAW RFC 822 submission |
| any other value | — | Startup failure |

Selection is configuration/DI only. The application does not switch implementations based on `spring.profiles.active`.

## Local

Mailpit is the default local sink (`implementation=mailpit`, `localhost:1025`).

Controlled Postfix is available for MTA-path testing:

- Compose service `postfix` on network `texto_app`
- SMTP published **only** to `127.0.0.1:2525` so the host-run API can submit (not a public listener)
- Accepts mail for virtual domain `texto.test` and controlled DSN domain `bounce.texto.test`
- Envelope `MAIL FROM` is `bounce+<token>@bounce.texto.test`; header `From` remains the verified sender
- `smtpd_relay_restrictions = reject_unauth_destination` (no `permit_mynetworks`, no `permit_all`, never `mynetworks = 0.0.0.0/0`)
- `smtpd_client_restrictions = permit_mynetworks, reject`
- `default_transport` / `relay_transport` = error — no MX delivery
- Hostname `mail.texto.test` via `POSTFIX_MYHOSTNAME` (not the container hostname)
- Application → Postfix: **plaintext SMTP** (`starttls.enabled=false`)
- Postfix → MX TLS is unused because outbound transport is error

```
MTA_IMPLEMENTATION=postfix
MTA_SMTP_HOST=localhost
MTA_SMTP_PORT=2525
MTA_SMTP_STARTTLS_ENABLED=false
MTA_SMTP_STARTTLS_REQUIRED=false
```

Amazon SES can be enabled locally when AWS credentials are available through the standard AWS SDK credential
provider chain:

```
MTA_IMPLEMENTATION=ses
SES_ENABLED=true
SES_REGION=ap-south-1
# Optional SES Configuration Set name; this is not the SNS topic name.
SES_CONFIGURATION_SET_NAME=
```

Required IAM permission: `ses:SendEmail`.

Do not configure AWS access keys or secret keys in source-controlled YAML. The SDK resolves credentials from
environment variables, AWS CLI/profile files, EC2/ECS roles, and the other standard `DefaultCredentialsProvider`
locations. SES Sandbox accounts can send only from verified identities to verified recipients until AWS grants
production access and quota. AWS controls production access, quotas, throttling, and sandbox exit.

`POSTFIX_ENABLE_PUBLIC_DELIVERY` defaults to `no`. Local compose hard-codes `no`. Enabling public MX requires `yes` **and** `POSTFIX_PUBLIC_DELIVERY_CONFIRM=ENABLE_PUBLIC_MX_DELIVERY`. See [production-smtp.md](production-smtp.md).

## Test

`application-test.yml` forces `implementation=mailpit` and disables `management.health.mta` so unit/ITs without an MTA still boot. `PostfixMtaIT` overrides implementation to `postfix` against a Testcontainers image built from `infrastructure/postfix`.

## Production

Production (`prod` profile) **fails fast** unless:

- `implementation=postfix` or `implementation=ses` (Mailpit is refused)
- SMTP port is not `1025`
- `MTA_SMTP_EHLO_HOSTNAME` is a real FQDN (not `texto.local` / `localhost` / `mail.texto.test`)
- STARTTLS is enabled **and** required, or implicit SSL is enabled
- TLS combinations are valid (required implies enabled; STARTTLS and implicit SSL are mutually exclusive)
- Timeouts and `max-rfc822-bytes` are positive
- `BOUNCE_DOMAIN` is set and is not a `*.texto.test` name
- `DKIM_KEY_ENCRYPTION_KEY` is a Base64 32-byte AES key

When `implementation=ses`, SMTP/Postfix-specific production checks do not apply. SES still uses the application
public-delivery kill switch: `EMAIL_PUBLIC_DELIVERY_ENABLED` defaults to **false**, so production SES handoff is
blocked until explicitly enabled. The SES client submits the immutable RFC 822 bytes from `MtaSubmitRequest.rfc822()`
through SESv2 `SendEmail` RAW content. It does not compose MIME, rewrite headers, or perform DKIM signing.

There is no trust-all certificate mode and no silent plaintext downgrade when STARTTLS is required. Certificate verification uses the JVM default trust store (`HTTPS` endpoint identification).

`EMAIL_PUBLIC_DELIVERY_ENABLED` defaults to **false**. Production Postfix will not submit to recipient MX until that flag is true **and** the Postfix image has `POSTFIX_ENABLE_PUBLIC_DELIVERY=yes` with confirm token. Repository configuration is not the same as a live public IP/DNS/PTR. See [production-smtp.md](production-smtp.md).

Production Postfix overlay (see `infrastructure/postfix/production.env.example`):

| Direction | Postfix parameter | Value | Why |
| --- | --- | --- | --- |
| Application → Postfix | `smtpd_tls_security_level` | `encrypt` | Internal submission. Postfix TLS_README / RFC 2487 forbid `encrypt` on a **publicly-referenced MX**; this instance is not a public MX. |
| Postfix → recipient MX | `smtp_tls_security_level` | `may` | Opportunistic TLS (RFC 3207). Do **not** set `encrypt`: some legitimate MX hosts still have no TLS. |

Certificates and keys come from secret management. They are not in git (`infrastructure/postfix/certs/` is ignored except `.gitkeep`).

Public Internet delivery remains **disabled** unless the dual kill switch is deliberately enabled. Local `default_transport=error` stays in `main.cf`.

Intended future topology (not enabled):

```
Internet
   ↑
Recipient MX servers
   ↑
Postfix outbound MTA (smtp.texto.email)
   ↑
Texto SMTP application / delivery worker
   ↑
RabbitMQ
   ↑
Texto API
```

## Hostname / EHLO

| Setting | Local default | Production (env, not source) |
| --- | --- | --- |
| Postfix `myhostname` / `smtp_helo_name` | `mail.texto.test` | e.g. `smtp.texto.email` |
| Postfix `mydomain` / `myorigin` | `texto.test` | e.g. `texto.email` |
| Application `MTA_SMTP_EHLO_HOSTNAME` | `texto.local` | same FQDN as the production MTA |

## Forward / reverse DNS

**Not configured in this repository.** Future production outbound IP must have:

- PTR: production IP → `smtp.texto.email`
- A/AAAA: `smtp.texto.email` → the same production IP

The IP is deployment configuration. This phase does not provision IPs or rDNS.

## Timeouts

Chosen to be bounded (no infinite waits) without being tighter than RFC 5321’s command windows on an internal submission path.

| Layer | Parameter | Value | Role |
| --- | --- | --- | --- |
| Application | `connection-timeout-ms` | 10000 | TCP connect |
| Application | `read-timeout-ms` / `write-timeout-ms` | 30000 | Greeting, commands, DATA, responses (one socket timeout) |
| Postfix smtpd | `smtpd_timeout` | 120s | Server command timeout |
| Postfix smtpd | `smtpd_starttls_timeout` | 60s | RFC 3207 handshake |
| Postfix smtp client | `smtp_connect_timeout` | 30s | Future MX connect |
| Postfix smtp client | `smtp_data_*_timeout` | 60–120s | Future MX DATA |

RFC 5321 §4.5.3.2 recommends up to 5 minutes for several SMTP commands on the public internet. This MTA is internal submission, so shorter bounds are intentional.

## Queue behavior

Two queues, two meanings:

1. **Application** — RabbitMQ + delivery state machine + retry. Covers MTA **handoff** failures (4xx, timeout, connection, TLS).
2. **Postfix** — incoming/active/deferred. Covers **recipient MX** delivery after accept. `maximal_queue_lifetime=1d` (and related backoff) is configured for a future smtp transport. Today `default_transport=error` and unauth destinations are rejected at `RCPT TO`, so this queue is not a second retry loop for Texto’s worker.

Do not treat Postfix 250 as recipient delivery. Do not ingest DSNs in this step.

## Message size

| Limit | Value |
| --- | --- |
| Postfix `message_size_limit` | 10485760 (10 MiB, Postfix default) |
| `email-platform.email.max-rfc822-bytes` | 10485760 |
| API `text` / `html` | 500_000 chars each (under the RFC 822 cap) |

The engine rejects oversized RFC 822 **before** SMTP with SMTP-shaped `552`. Limits are configuration-driven; they were not increased above Postfix’s default.

## Connection / resource limits (Postfix)

Not tenant quotas. Tenant entitlements stay in the application.

- `default_process_limit=20`
- `smtpd_client_connection_count_limit=10`
- `smtpd_client_connection_rate_limit=30`
- `smtpd_client_message_rate_limit=60`
- `smtpd_recipient_limit=50` (aligned with `email-platform.email.max-recipients`)
- `smtpd_hard_error_limit=20`
- `disable_vrfy_command=yes`

## Health checks

| Check | What it proves | What it does not prove |
| --- | --- | --- |
| Container `/smtp-ping.sh` | `postfix status` + RFC 5321 `220` on :25 | Recipient delivery |
| Actuator `mta` | Application can connect and read `220` | Recipient delivery, TLS to MX |

Neither probe sends `MAIL FROM` / `DATA`. Test profile disables the actuator contributor so suites without an MTA still pass.

## DKIM

Signing keys come from encrypted `dkim_keys` custody. There is no in-process key generation at delivery and no unsigned fallback. See [dkim-key-custody.md](dkim-key-custody.md).

MIME compose → DKIM sign → immutable RFC 822 → `MtaClient` → Postfix.

Signed headers: From, To, Cc (optional), Reply-To (optional), Subject, Date, Message-ID, MIME-Version, Content-Type.

Postfix prepends `Received` (and may add `Return-Path` / `Delivered-To` on virtual delivery). Those names are **not** in `h=`, so they do not invalidate the signature. `local_header_rewrite_clients` is empty so Postfix does not complete From/To with `$myorigin`. `append_dot_mydomain=no`. Postfix must not regenerate DKIM.

`PostfixMtaIT` stores the maildir copy and verifies DKIM over the queued bytes.

## SPF / DMARC

Receivers evaluate SPF/DKIM/DMARC. Texto does not compute PASS. Production DNS requirements: [production-smtp.md](production-smtp.md).

## Required production infrastructure

See [production-smtp.md](production-smtp.md). Public IP, PTR, SPF, DKIM DNS, DMARC, inbound bounce MX, and warmup are **external**. The repository kill switch stays off until those exist.

