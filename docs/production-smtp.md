# Production outbound SMTP

Public Internet delivery is **not enabled** and is **not production-ready**.
This document is the configuration contract and external-infrastructure checklist.
A Postfix `250` after `DATA` means the remote MTA accepted the message, not inbox delivery.

Do not mark any checkbox below complete unless that item has actually been provisioned and verified.

---

## EXTERNAL INFRASTRUCTURE

- [ ] Static public IPv4
- [ ] A record (`MTA_HOSTNAME` → `MTA_OUTBOUND_IP`)
- [ ] PTR/rDNS (`MTA_OUTBOUND_IP` → `MTA_HOSTNAME`)
- [ ] SMTP hostname
- [ ] SPF (authorizes `MTA_OUTBOUND_IP`; include `_spf.texto.email` is **not** published yet)
- [ ] Customer DKIM TXT
- [ ] Customer DMARC TXT
- [ ] Submission TLS certificate (application → Postfix)
- [ ] Public bounce MX
- [ ] Monitoring

## APPLICATION

- [ ] Redis available
- [ ] Rate limiting enabled
- [ ] Monthly quota enabled
- [ ] Suppression enabled
- [ ] Bounce ingestion enabled (local trusted path only today)
- [ ] Complaint ingestion enabled (trusted ingress only; no public FBL)
- [ ] DKIM encryption key configured
- [ ] Public delivery switch intentionally enabled

---

## IMPLEMENTED IN REPOSITORY

- `MtaClient` / `PostfixMtaClient` (application never speaks to recipient MX)
- Async API → outbox → RabbitMQ → worker → `SmtpDeliveryEngine`
- DKIM signing from encrypted `dkim_keys` (fail-closed; Postfix does not re-sign)
- Bounce envelope `MAIL FROM: bounce+<token>@<BOUNCE_DOMAIN>` + `ENVID=<token>`
- No application-injected `Return-Path` header
- Dual kill switch (application + Postfix), default **off**
- Production fail-fast: Postfix, public `MTA_HOSTNAME`, public `MTA_OUTBOUND_IP`, STARTTLS required, production `BOUNCE_DOMAIN`, `DKIM_KEY_ENCRYPTION_KEY`, rate-limit bounds
- Readiness details distinguish `NOT_CONFIGURED` / `CONFIGURED` / `VERIFIED` / `BLOCKED`
- `productionInternetReady` is true only when DNS A, PTR, bounce MX, Redis, TLS, DKIM encryption, bounce domain, SPF authorization, and the application public-delivery switch are all actually satisfied. It is **false** in this repository today.
- Health `ptrVerified` is true only after a real PTR lookup matches `MTA_HOSTNAME`

---

### Identity

| Setting | Env | Production rule |
| --- | --- | --- |
| SMTP hostname | `MTA_HOSTNAME` (EHLO must match) | Required FQDN. Reject `localhost`, `*.local`, `*.test`, `*.localhost` |
| Postfix HELO | `POSTFIX_MYHOSTNAME` = `POSTFIX_SMTP_HELO_NAME` | Same FQDN. Not the container hostname |
| Outbound IPv4 | `MTA_OUTBOUND_IP` / `POSTFIX_SMTP_BIND_ADDRESS` | Required public IPv4. Reject loopback, RFC1918, link-local, multicast, TEST-NET, CGNAT |
| Bounce domain | `BOUNCE_DOMAIN` | Required real DNS name. Not `*.test` |

Postfix must originate Internet SMTP from `MTA_OUTBOUND_IP`. The Java client does not discover that IP and does not speak to recipient MX.

### Forward DNS vs PTR

```
<MTA_HOSTNAME>     A      <MTA_OUTBOUND_IP>
<MTA_OUTBOUND_IP>  PTR    <MTA_HOSTNAME>
```

Readiness:

- Hostname/IP present → `CONFIGURED`
- A record matches the configured IP → `forwardDns=VERIFIED`
- PTR names include `MTA_HOSTNAME` → `ptr=VERIFIED` / `ptrVerified=true`
- Lookup failure or mismatch → `BLOCKED` (never `VERIFIED`)

Startup does **not** depend on public DNS. Health probes are diagnostic only.

### SPF

Receivers evaluate SPF (RFC 7208), including the **10 DNS-lookup** limit. Texto does **not** claim SPF PASS.

Customer TXT is generated as:

- Default: `v=spf1 include:<SPF_INCLUDE> ~all` with `SPF_INCLUDE=_spf.texto.email`
- **`_spf.texto.email` is not provisioned.** Do not tell customers that include is live.
- When `SPF_AUTHORIZE_OUTBOUND_IP=true` and `MTA_OUTBOUND_IP` is a public IPv4: `v=spf1 ip4:<MTA_OUTBOUND_IP> ~all`
- Set `SPF_INCLUDE_PROVISIONED=true` only after the include name actually exists in DNS

### DKIM

Customer verified domain → ACTIVE `dkim_keys` row → decrypt → `DkimSigner` → `DKIM-Signature` → Postfix.

DNS: `<selector>._domainkey.<domain> TXT v=DKIM1; k=rsa; p=<active public key>`

Signing failure does not send unsigned mail. Health reports DKIM **encryption key configured**, not per-tenant ACTIVE-key inventory. Send path still requires an ACTIVE key.

### DMARC

Separate `_dmarc.<domain>` TXT. Default `p=none`. Do not force `p=reject`. Texto does not claim DMARC PASS.

Alignment:

- DKIM `d=` ↔ header `From` domain
- SPF MAIL FROM / bounce domain is the envelope, not the header From

### TLS

| Path | Policy |
| --- | --- |
| Application → Postfix | Production: STARTTLS enabled **and** required (or implicit SSL). JVM default trust; HTTPS endpoint identification. No trust-all. Certs are not in Git. See `production.env.example`. |
| Postfix → recipient MX | Opportunistic `smtp_tls_security_level=may`. Do not set `encrypt` globally. |

### Bounce MX / complaints

Envelope remains `MAIL FROM: bounce+<token>@<BOUNCE_DOMAIN>` + `ENVID`. No `Return-Path` header.

Public inbound MX is **external** and **not implemented**:

```
bounce.example.com  MX  inbound-bounce.example.com
Internet DSN → trusted ingress → email.bounce → BounceDsnWorker → DsnIngestionService
```

Set `BOUNCE_MX_HOSTNAME` only when that MX name exists. Readiness `bounceMx=VERIFIED` means DNS MX records were found, not that DSN ingestion from the Internet works.

Complaint/FBL: trusted ingress only. Future work is provider-specific (Gmail, Yahoo, Outlook). No generic public FBL endpoint.

### Dual kill switch

Both required, both default off:

1. `EMAIL_PUBLIC_DELIVERY_ENABLED=true`
2. `POSTFIX_ENABLE_PUBLIC_DELIVERY=yes` **and** `POSTFIX_PUBLIC_DELIVERY_CONFIRM=ENABLE_PUBLIC_MX_DELIVERY`

Local compose hard-codes Postfix `no`. `true`/`1` is rejected by the image.

### Enable / rollback

Do not enable until the checklists above are actually complete. Rollback: application switch first, then Postfix switch. Queued mail may already be in Postfix.

Placeholders: `production.env.example` and `infrastructure/postfix/production.env.example`.
