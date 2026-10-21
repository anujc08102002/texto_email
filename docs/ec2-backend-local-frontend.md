# Run the backend on EC2 and the frontend on your Windows PC

This reproduces the earlier setup while keeping PostgreSQL, RabbitMQ, Redis, AWS credentials and workers on EC2. The local frontend calls that backend. Pulling from Git transfers source code; it does not transfer environment files, databases or AWS permissions.

The changes in this release add **Tenants → Add tenant**, a protected admin provisioning API, real initial-plan assignment, and SPF verification compatible with additional positive `include:` mechanisms in one record. Public signup remains disabled in production.

## 1. Choose the existing installation or a fresh installation

First, on EC2:

```bash
cat /etc/os-release
sudo ss -lntp
sudo systemctl status texto-email-backend --no-pager
sudo docker ps
```

If your existing backend has real data, retain its database, volumes, environment and encryption keys. Back it up before updating. Do not run a fresh configuration generator over that installation, replace its containers, or switch it to an empty database. Your earlier manually started backend might use port **8080**; the supplied systemd deployment uses **18080**. Use its actual port consistently below.

For a **new** installation, follow sections 2–5 of [the Ubuntu deployment guide](production-deployment.md): Java 21, Docker Compose, service account, configuration generation, persistent dependencies and backend build. You can skip the EC2 frontend Node installation/build/service when using only your Windows frontend. The new deployment stores config in `/etc/texto-email/backend.env`; a repository `.env` is not automatically read by Java or systemd.

The supplied Compose file creates separate volumes and ports; it is not an automatic migration of the old setup. A fresh staging configuration is generated with:

```bash
cd /opt/texto-email
sudo python3 scripts/prepare-production.py --environment staging --admin-email textoadmin@texto.in
sudo chown root:texto /etc/texto-email/*.env
sudo chmod 640 /etc/texto-email/*.env
```

Run that only once, on a new installation. Set the bootstrap password securely with `sudoedit /etc/texto-email/backend.env` before its first startup. Bootstrap creates the first platform admin only; it does not reset an existing admin account.

## 2. Pull and deploy the changed backend

Push the reviewed application changes to your existing repository from your PC first. Include the new controller, scripts and docs; exclude `.env`, `.env.local`, credentials, dependencies and generated build output. Use your existing repository URL and branch; none is assumed here.

On EC2, in your existing checkout (the following uses `/opt/texto-email`):

```bash
cd /opt/texto-email
git status --short
git pull --ff-only
sudo -u texto bash -c 'cd /opt/texto-email/backend && bash ./mvnw -DskipTests package'
sudo install -d -o texto -g texto /opt/texto-email/runtime
sudo install -o texto -g texto -m 0640 backend/target/email-platform-0.1.0-SNAPSHOT.jar runtime/email-platform.jar
sudo install -m 0644 infrastructure/production/texto-email-backend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now texto-email-backend
sudo systemctl restart texto-email-backend
sudo journalctl -u texto-email-backend -n 80 --no-pager
curl --fail http://127.0.0.1:18080/actuator/health
```

Adapt the service account/path if your existing checkout differs. Do not install the new service alongside an older backend still processing the same database/queues. Stop the old worker before starting its replacement. Wait for migrations/startup before retrying health. `UP` proves application/dependency health, not delivery to a mailbox.

## 3. Set the backend environment on EC2

Keep the generated/existing database, broker, Redis, attachment and encryption settings. Edit the following values in the **environment file actually loaded by the backend service**:

```dotenv
SPRING_PROFILES_ACTIVE=prod
SERVER_ADDRESS=127.0.0.1
SERVER_PORT=18080
MTA_IMPLEMENTATION=ses
SES_ENABLED=true
SES_REGION=ap-south-1
SES_CONFIGURATION_SET_NAME=my-first-configuration-set
EMAIL_PUBLIC_DELIVERY_ENABLED=false
ALLOW_PLATFORM_TEST_SENDERS=false
SPF_INCLUDE=amazonses.com
SPF_INCLUDE_PROVISIONED=true
SPF_AUTHORIZE_OUTBOUND_IP=false
DKIM_SELECTOR=texto
DMARC_POLICY=none
CORS_ALLOWED_ORIGINS=http://localhost:3000,http://127.0.0.1:3000,https://email.texto-qa.in
EMAIL_PUBLIC_BASE_URL=https://email-api.texto-qa.in
MTA_HOSTNAME=email-api.texto-qa.in
BOUNCE_DOMAIN=bounce.notify.texto-qa.in
SES_FEEDBACK_ENABLED=false
SES_FEEDBACK_QUEUE_URL=
```

These are staging names for your `texto-qa.in` domain. Keep your previously verified sending domain if it differs; change the MAIL FROM name and SES identity together. Do not paste this block over the complete environment file: it deliberately omits database credentials and encryption keys. `DKIM_KEY_ENCRYPTION_KEY` and `WEBHOOKS_ENCRYPTION_KEY` must remain stable across deployments and migrations.

For the EC2 SDK, use its attached IAM instance role. The Windows frontend needs **no AWS keys**. The backend needs `ses:SendEmail`; the admin SES readiness check also needs `ses:GetAccount`, `ses:GetEmailIdentity`, `ses:GetConfigurationSet` and `ses:GetConfigurationSetEventDestinations`. Feedback needs `sqs:ReceiveMessage`, `sqs:DeleteMessage`, `sqs:GetQueueAttributes`, `sqs:ChangeMessageVisibility` on the feedback queue, plus applicable KMS rights if encrypted with a customer key. Scope permissions to the identities/configuration set/queue where the AWS action supports it.

Restart after every backend environment change:

```bash
sudo systemctl restart texto-email-backend
sudo journalctl -u texto-email-backend -n 60 --no-pager
```

## 4. Connect the local frontend

Recommended for initial testing: keep the API bound to EC2 loopback and use SSH forwarding. On Windows, keep this terminal open, replacing the key path and host with your existing SSH details:

```powershell
ssh -i "C:\path\to\your-key.pem" -N -o ExitOnForwardFailure=yes -o ServerAliveInterval=30 -L 18080:127.0.0.1:18080 ubuntu@YOUR_EC2_HOST
```

The last port must be the actual backend port on EC2. If the existing service uses 8080, use `-L 18080:127.0.0.1:8080`. Never point this tunnel at your local Java process. Choose a different unused local port if 18080 is occupied.

In a second Windows PowerShell terminal:

```powershell
cd D:\TEXTO_Projects\Texto_email
.\scripts\start-ec2-frontend.ps1 -Install
```

This installs the frontend dependencies, checks EC2 health through the tunnel, and starts the frontend at `http://localhost:3000` with both API variables set to `http://localhost:18080`. Stop any existing frontend on port 3000 first. Later runs can omit `-Install`. For a different local tunnel port, pass `-ApiBaseUrl http://localhost:18081`.

For a persistent, public HTTPS API, configure `email-api.texto-qa.in` A record, Nginx and TLS using section 6 of the deployment guide, then instead run:

```powershell
.\scripts\start-ec2-frontend.ps1 -ApiBaseUrl https://email-api.texto-qa.in
```

The equivalent manual local frontend settings are:

```dotenv
NEXT_PUBLIC_API_BASE_URL=http://localhost:18080
API_BASE_URL=http://localhost:18080
NEXT_PUBLIC_WHATSAPP_BASE_URL=https://texto.in
```

Put those in `frontend/.env.local` only if starting manually with `npm run dev`. The launcher supplies process variables, which take precedence. `API_BASE_URL` also directs the Next.js admin-login proxy; setting only the browser variable can accidentally split login from other requests. Restart development after changing URLs; rebuild production bundles because [`NEXT_PUBLIC_*` values are compiled into the browser JavaScript](https://nextjs.org/docs/app/guides/environment-variables).

Do **not** use `start-local.ps1` for this topology: it explicitly selects the local backend/Mailpit setup. Open the browser Network panel and confirm requests go to port 18080 or your HTTPS EC2 API, rather than the old local port 8080. Keep only SSH 22 restricted to your IP and public HTTPS 443/HTTP 80 in EC2 ingress; do not expose PostgreSQL, RabbitMQ, Redis or the raw Java port.

## 5. Onboard a tenant and verify its DNS

1. Open `http://localhost:3000/admin/login`, sign in with the platform admin stored in the EC2 database, then **Tenants → Add tenant**.
2. Enter organization, owner email and owner password. Select the real initial plan. **Free** supports one custom domain and API sending; **Starter** or above enables webhooks in the seeded catalog. Admin grants enable entitlements without collecting payment. Paid recurring checkout still requires a configured billing provider.
3. Create once, open its details, and check the subscription. A duplicate owner email returns a conflict. No owner session replaces your admin session. The app does not send an invitation email automatically; share the credentials securely.
4. Sign in as the owner at `/login` in another browser profile. Add a sending domain you control, preferably `notify.texto-qa.in` for this first test. Do not register somebody else's domain or reuse a sending domain already verified by another tenant.
5. Copy the **exact** DNS names and values from the tenant domain screen. The ownership token and DKIM public key are generated for that domain; a generic example cannot replace them.

| Purpose | Example full DNS name | Type | Value source |
| --- | --- | --- | --- |
| Texto ownership | `texto-verify.notify.texto-qa.in` | TXT | App-generated token |
| Texto SPF check | `notify.texto-qa.in` | TXT | App-generated policy; for SES normally `v=spf1 include:amazonses.com ~all` |
| Texto DKIM | `texto._domainkey.notify.texto-qa.in` | TXT | App-generated RSA public key |
| Texto DMARC check | `_dmarc.notify.texto-qa.in` | TXT | App-generated policy; default `v=DMARC1; p=none` |
| SES Easy DKIM | Three distinct selector names shown by SES | CNAME | Exact SES targets in ap-south-1 |
| SES custom MAIL FROM | `bounce.notify.texto-qa.in` | MX and TXT | Exact SES values; separate from the app's SPF name |

Use your DNS provider's relative-name convention: in zone `texto-qa.in`, the ownership host might be `texto-verify.notify`. Some providers want the full name. Check the final public name to avoid accidentally creating `...texto-qa.in.texto-qa.in`. TXT records must be DNS-only; split long DKIM public keys into quoted character strings **within one TXT record**, not several records. Do not publish the private key. Do not replace the root domain's inbound mailbox MX records.

There must be exactly **one** SPF record at each name. Additional positive `include:` mechanisms may share the app's required policy. Arbitrary SPF mechanisms/redirect policies are not evaluated by this verifier; use a dedicated sending subdomain with the displayed record if your existing root policy is more complex. DMARC currently must match the configured `DMARC_POLICY`; do not weaken an existing domain's policy to pass a staging test. Use a dedicated subdomain or configure the intended matching policy.

Check from EC2, where application verification actually runs:

```bash
dig +short NS texto-qa.in
dig +short TXT texto-verify.notify.texto-qa.in
dig +short TXT notify.texto-qa.in
dig +short TXT texto._domainkey.notify.texto-qa.in
dig +short TXT _dmarc.notify.texto-qa.in
dig +short MX bounce.notify.texto-qa.in
dig +short TXT bounce.notify.texto-qa.in
```

Then click **Verify**. Missing/wrong DNS must remain failed/pending, never manually forced to verified. Allow authoritative publication and cached negative TTLs to expire before retrying; rate limits apply. EC2 must resolve DNS over UDP/TCP 53 through its configured resolver. The DNS status comes from backend lookups, independent of the local frontend.

## 6. Complete the separate SES checks, then send once

In AWS SES **Mumbai / ap-south-1**:

1. Verify that same sending domain identity. Enable Easy DKIM and publish its CNAMEs; wait for success. Texto's `texto` DKIM selector and SES's selectors coexist.
2. Set the custom MAIL FROM domain beneath that identity. Publish the MX and TXT shown by SES. For the example, the MX is normally priority 10 to `feedback-smtp.ap-south-1.amazonses.com`, and TXT `v=spf1 include:amazonses.com ~all`. Follow the actual [SES custom MAIL FROM instructions](https://docs.aws.amazon.com/ses/latest/dg/mail-from.html).
3. Check sending is enabled, configuration set `my-first-configuration-set` exists and is enabled, and account quotas permit your test. In the SES sandbox, recipients must also be verified or use an AWS mailbox simulator; requesting production access is separate from domain verification.
4. In **Admin → System → SES readiness**, check the exact sending identity. A configured feedback queue is not proof that the SNS subscription or consumer works.
5. Set `EMAIL_PUBLIC_DELIVERY_ENABLED=true` in EC2 `backend.env`, restart, and send one message from the verified tenant address to a mailbox you control. Use the tenant composer or a tenant **LIVE** API key with sending scope, not an admin token or TEST key. Keep volumes low while checking results.
6. Record the app message ID and SES provider message ID. Confirm receipt in the mailbox and inspect its original headers for SES DKIM/SPF/DMARC. An API acceptance or app `DELIVERED` state after provider submission does not by itself prove inbox placement.

## 7. Feedback and customer webhooks are different paths

**AWS feedback:** SES configuration-set event destination → SNS Standard topic → SQS Standard queue → EC2 consumer. Configure only **Bounce** and **Complaint** event types for this consumer's queue; it does not handle Delivery/Open/Click event payloads. Use configuration-set event publishing (`eventType`), not the legacy identity notification shape (`notificationType`). See [AWS event payloads](https://docs.aws.amazon.com/ses/latest/dg/event-publishing-retrieving-sns-contents.html).

Create/inspect the SNS subscription to the SQS queue. Its queue policy must allow `sqs:SendMessage` from `sns.amazonaws.com`, restricted to that exact SNS topic ARN; the topic policy must permit SES event publishing with your account restriction. Use a separate SQS DLQ/redrive policy. The EC2 role needs queue consumption permissions. Raw SNS delivery or the SNS JSON envelope is supported by the consumer. Then set:

```dotenv
SES_FEEDBACK_ENABLED=true
SES_FEEDBACK_QUEUE_URL=https://sqs.ap-south-1.amazonaws.com/YOUR_ACCOUNT/YOUR_FEEDBACK_QUEUE
```

Restart the backend. Send controlled tests through the app to `bounce@simulator.amazonses.com` and `complaint@simulator.amazonses.com`. Confirm events reach SQS, are consumed, appear against the original message in Bounces/Complaints, and create the appropriate suppression. Check the SQS DLQ and backend logs if correlation fails. Do not use `aws sqs receive-message` concurrently to inspect this live queue: that temporarily hides messages from the application.

**Customer webhooks:** EC2 → tenant's public HTTPS receiver. In the Starter-or-above tenant, add the receiver and subscriptions, securely save the one-time signing secret, and use **Send test**. The receiver cannot be your PC's `localhost`; the EC2 worker sees its own localhost. Private/loopback destinations are deliberately blocked. Use an HTTPS receiver you control and can inspect, not a random third-party collector for customer data.

The exact API flow uses the owner bearer token: `POST /api/v1/webhooks`, then `POST /api/v1/webhooks/{id}/test`, then `GET /api/v1/webhooks/{id}/events`. Create body example:

```json
{"url":"https://YOUR_CONTROLLED_RECEIVER/events","description":"Staging verification","eventTypes":["email.queued","email.delivered","email.bounced","email.complaint"]}
```

The receiver verifies `Webhook-Signature: t=<unix-seconds>,v1=<hex>` using HMAC-SHA256 over `<unix-seconds>.<exact raw body>` with the returned signing secret. Compare in constant time, enforce a recent timestamp (for example five minutes), and deduplicate using `Webhook-Id`. Return 2xx after durable acceptance. Do not parse/reformat JSON before signature verification. Retries reuse the event ID and use a fresh signature timestamp; check the app event history for attempts, response codes and failure reasons.

If testing campaigns, configure public API DNS/HTTPS before sending: unsubscribe links use `EMAIL_PUBLIC_BASE_URL`, so an SSH tunnel alone cannot make recipients' links public. Verify a received message's unsubscribe URL from outside your PC and check that subsequent sends respect suppression.

## 8. Acceptance checks and common failures

| Check | Required result / first place to investigate |
| --- | --- |
| Health through the Windows tunnel | `UP`; otherwise SSH forwarding, service logs, dependencies |
| Browser API calls | EC2 URL/forwarded port; CORS and CSP allow that origin |
| Tenant creation | Appears in directory; owner login and selected plan work; public signup stays closed |
| DNS before publication | Verification fails with a specific record error |
| DNS after publication | All four app records verified; SES identity/DKIM checked separately |
| `FEATURE_NOT_AVAILABLE` | Tenant's actual plan; webhooks need Starter or above |
| SES access denied | Instance role and effective service credentials, not frontend env |
| Sender/recipient unverified | SES identity in correct region and sandbox recipient rules |
| Messages remain queued | RabbitMQ consumer/outbox/worker logs and delivery kill switch |
| Bounce/complaint missing | Configuration-set event destination, SNS/SQS policy, consumer logs and DLQ |
| Webhook failed | Public HTTPS reachability, exact-body HMAC, receiver status, retry history |
| Restart recovery | Accounts/templates/DKIM/webhook secrets/attachments still usable |

The source and automated checks can validate application behavior here. Actual EC2 IAM access, authoritative customer DNS, SES sending, public TLS, SQS feedback and customer receiver delivery must be verified on your infrastructure using the checks above. No remote deployment or AWS resource change is performed by this guide.
