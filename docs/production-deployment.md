# Texto email: Ubuntu deployment and physical-server migration

Prepared 21 October 2026. This is a single-server deployment plan for the current source tree, not evidence that the remote infrastructure is configured. Run and validate staging before accepting production traffic. Ubuntu 24.04/26.04 LTS is the intended target; confirm with `cat /etc/os-release`. These commands belong on Linux, not your Windows PC.

## 1. Where each application lives

| Environment | Website | API and public unsubscribe links | Host |
| --- | --- | --- | --- |
| Staging | `email.texto-qa.in` | `email-api.texto-qa.in` | Existing dedicated email EC2 |
| Production | `email.texto.in` | `email-api.texto.in` | Purchased physical server |
| WhatsApp | Existing website and routes | Existing WhatsApp API | Its existing separate server |

The `email.*` names are proposed new subdomains. They avoid replacing either root website. Configure the email app's WhatsApp navigation for the existing WhatsApp origin. This deployment does not merge authentication or route both apps through a single website; that integration is separate work.

EC2 is optional after migration. SES, SNS and SQS remain in AWS. A physical server needs its own AWS authentication; it cannot inherit an EC2 instance profile. Keep the EC2 installation available for rollback until migration and restore checks pass.

For the physical server, arrange a stable public IP, inbound TCP 80/443, outbound HTTPS, DNS resolution, time synchronization, reliable power, and off-site backups. If behind a router, forward 80/443 to the server. CGNAT without inbound public access needs an ISP public IP or another ingress solution. Do not point production DNS at an unreachable private address.

Planning estimate for this all-in-one setup: 4 vCPU, 8 GB RAM and SSD storage with room for database growth, uploads, logs, backups, and builds. This is a starting allocation, not a measured capacity guarantee. RAID is not an off-site backup. A single server has no automatic failover.

## 2. Network and DNS first

Use an Elastic IP for the staging EC2 address, or verify the public IP is stable. In the EC2 security group allow 443 and 80 publicly, and SSH 22 only from your administrator IP. Do not expose the application, database, broker, cache or management ports.

Create these A records in the authoritative DNS zone. The value is the corresponding host's public IP, not the SES endpoint:

| Zone | Record name | Type | Value |
| --- | --- | --- | --- |
| `texto-qa.in` | `email` | A | EC2 stable public IPv4 |
| `texto-qa.in` | `email-api` | A | Same EC2 IPv4 |
| `texto.in` | `email` | A | Physical-server public IPv4 |
| `texto.in` | `email-api` | A | Same physical-server IPv4 |

Use a low TTL such as 300 during cutover. Publish AAAA records only after IPv6 is configured and tested. Start DNS-only if using Cloudflare; email verification records must not be proxied. Leave existing root A, MX and WhatsApp records in place.

Check from another network:

```bash
dig +short A email.texto-qa.in
dig +short A email-api.texto-qa.in
```

## 3. Install the host dependencies

First inspect existing services and volumes. Do not overwrite an existing installation or point this fresh Compose deployment at a live old database. New production ports are 15433 (PostgreSQL), 15673 (AMQP), 15674 (broker UI), 16379 (Redis), 18080 (API), and 13000 (Next.js), all on loopback.

```bash
cat /etc/os-release
sudo ss -lntp
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg git rsync unzip xz-utils \
  openssl python3 openjdk-21-jdk nginx dnsutils snapd
java -version
```

Install Docker Engine and Compose using [Docker's Ubuntu repository instructions](https://docs.docker.com/engine/install/ubuntu/). Do not uninstall an existing working Docker installation blindly. For a clean host:

```bash
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker nginx
docker compose version
```

Use [Node.js 24 LTS](https://nodejs.org/en/download). The following installs the official binary after verifying its published checksum. Run once on this dedicated email host; inspect existing `/usr/local/bin/node` first if Node is already installed.

```bash
case "$(uname -m)" in
  x86_64) NODE_ARCH=x64 ;;
  aarch64) NODE_ARCH=arm64 ;;
  *) echo 'Unsupported architecture for this command'; exit 1 ;;
esac
NODE_VERSION=v24.22.0
NODE_DIST="node-${NODE_VERSION}-linux-${NODE_ARCH}"
NODE_TMP=$(mktemp -d)
cd "$NODE_TMP"
curl -fSLO "https://nodejs.org/dist/${NODE_VERSION}/${NODE_DIST}.tar.xz"
curl -fSLO "https://nodejs.org/dist/${NODE_VERSION}/SHASUMS256.txt"
awk -v archive="${NODE_DIST}.tar.xz" '$2 == archive' SHASUMS256.txt | sha256sum --check -
sudo install -d /usr/local/lib/nodejs
sudo tar -xJf "${NODE_DIST}.tar.xz" -C /usr/local/lib/nodejs
sudo ln -sfn "/usr/local/lib/nodejs/${NODE_DIST}/bin/node" /usr/local/bin/node
sudo ln -sfn "/usr/local/lib/nodejs/${NODE_DIST}/bin/npm" /usr/local/bin/npm
/usr/local/bin/node --version
/usr/local/bin/npm --version
```

The checksum is fetched over HTTPS from the publisher; signed SHASUM verification is also documented on the download page. Review supported patch versions before future installations.

## 4. Upload source and create the service account

Create a dedicated unprivileged account. Do not run Java or Next.js as root.

```bash
sudo useradd --system --user-group --home-dir /var/lib/texto-email --create-home --shell /usr/sbin/nologin texto
sudo install -d -o texto -g texto -m 0750 /opt/texto-email
sudo install -d -o texto -g texto -m 0700 /var/lib/texto-email/attachments
sudo install -d -o root -g texto -m 0750 /etc/texto-email
```

If `texto` already exists, inspect it and reuse it rather than recreating it. Upload the project into `/opt/texto-email` using your existing SSH/SFTP access. Include `backend/.mvn`, the Maven wrapper, frontend lockfile, scripts, and infrastructure files. Exclude Windows `node_modules`, `.next`, `target`, local `.env`, `.git`, logs and review/test files. Rebuild dependencies on Linux.

```bash
sudo chown -R texto:texto /opt/texto-email
```

For a brand-new staging installation, generate matching secrets and host configuration:

```bash
cd /opt/texto-email
sudo python3 scripts/prepare-production.py --environment staging --admin-email YOUR_ADMIN_EMAIL
sudo chown root:texto /etc/texto-email/*.env
sudo chmod 640 /etc/texto-email/*.env
```

For a brand-new physical production installation use `--environment production`. For migration of an existing live database, copy its original database credentials and encryption keys instead of generating replacements. The generator refuses to overwrite existing configuration. Root can inspect the first admin password with `sudoedit /etc/texto-email/backend.env`; do not paste it into chat or commit it.

Files generated:

- `backend.env`: database/broker/cache credentials, persistent attachment root, encryption keys and first admin bootstrap.
- `frontend.env`: public API URL, internal admin-login API URL, WhatsApp origin.
- `dependencies.env`: matching credentials for the containers.
- `nginx.conf`: virtual hosts for this environment.

Sending and SES feedback are deliberately initially off. Billing is `noop`, so paid checkout is not represented as working. Production never enables fabricated `*.texto.test` senders.

## 5. Start persistent data services and build the app

This Compose file creates NEW, separate production volumes. It does not migrate an earlier installation. Existing PostgreSQL/RabbitMQ volumes require a separate backup and migration plan; do not change major versions against old volumes.

```bash
cd /opt/texto-email
sudo docker compose --env-file /etc/texto-email/dependencies.env \
  -f infrastructure/production/dependencies.compose.yml config --quiet
sudo docker compose --env-file /etc/texto-email/dependencies.env \
  -f infrastructure/production/dependencies.compose.yml up -d --wait
sudo docker compose --env-file /etc/texto-email/dependencies.env \
  -f infrastructure/production/dependencies.compose.yml ps
sudo -u texto bash -c 'cd /opt/texto-email/backend && bash ./mvnw "-Dtest=*Test" test'
sudo -u texto bash -c 'cd /opt/texto-email/backend && bash ./mvnw -DskipTests package'
sudo install -d -o texto -g texto /opt/texto-email/runtime
sudo install -o texto -g texto -m 0640 \
  backend/target/email-platform-0.1.0-SNAPSHOT.jar runtime/email-platform.jar
sudo -u texto bash -c 'cd /opt/texto-email/frontend && npm ci && npm run lint && npm run typecheck'
sudo -u texto bash -c 'set -a; source /etc/texto-email/frontend.env; set +a; cd /opt/texto-email/frontend; npm run build'
```

Full integration tests need Docker access and a disposable test environment; do not run them against production data. Validate the new RabbitMQ version and the full queue/SES pipeline in staging before cutover. The Compose file selects RabbitMQ 4.3.6, a supported series when prepared; [review its lifecycle](https://www.rabbitmq.com/release-information). Do not replace an existing 3.13 broker in place with this image.

`NEXT_PUBLIC_API_BASE_URL` is included in the browser bundle at build time. Rebuild after changing it; restarting alone will not change it. Nginx in front of Next.js follows the [Next.js self-hosting guidance](https://nextjs.org/docs/app/guides/self-hosting).

Install the supplied services:

```bash
sudo install -m 0644 infrastructure/production/texto-email-backend.service /etc/systemd/system/
sudo install -m 0644 infrastructure/production/texto-email-frontend.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now texto-email-backend
curl --fail http://127.0.0.1:18080/actuator/health
sudo systemctl enable --now texto-email-frontend
curl --fail --head http://127.0.0.1:13000/
```

The first health request may fail while startup/migrations run: inspect `journalctl -u texto-email-backend -n 100 --no-pager`, wait for startup completion, then retry. Flyway runs automatically; never delete `flyway_schema_history`, enable Hibernate schema updates, or run destructive setup SQL. `UP` does not prove SES access, live delivery, or feedback processing.

## 6. Configure HTTPS

```bash
sudo install -m 0644 /etc/texto-email/nginx.conf /etc/nginx/sites-available/texto-email
sudo ln -s /etc/nginx/sites-available/texto-email /etc/nginx/sites-enabled/texto-email
sudo nginx -t
sudo systemctl reload nginx
sudo snap install --classic certbot
sudo /snap/bin/certbot --nginx -d email.texto-qa.in -d email-api.texto-qa.in
sudo /snap/bin/certbot renew --dry-run
```

Use the `texto.in` hostnames for the production host. If the symlink or Certbot installation already exists, inspect/reuse it. Follow [Certbot's Nginx instructions](https://certbot.eff.org/instructions?ws=nginx&os=snap). DNS must already point to this host and ports 80/443 must be reachable. Verify certificate auto-renewal is scheduled by the installed Certbot package.

Confirm from outside the server that HTTPS works, HTTP redirects to HTTPS, logins succeed, and all internal ports are inaccessible. If using UFW, permit your SSH access and 80/443 before enabling it. Container loopback bindings are intentional; also enforce the EC2 security group or physical perimeter firewall.

The API proxy preserves `/api/v1/public/unsubscribe/*` and disables access logs for token URLs. Keep the unsubscribe route outside authentication, CAPTCHA or cache challenges. Never cache authenticated API responses at a CDN.

## 7. SES authentication on EC2 versus the physical server

On EC2, keep the existing `TextoEmailProductionRole` attached. Do not add static keys merely because the application needs AWS access. Verify credentials as the service account, not only as your SSH user. Install AWS CLI v2 using AWS's official installer if needed.

On the physical server, preferred authentication is [IAM Roles Anywhere](https://docs.aws.amazon.com/rolesanywhere/latest/userguide/introduction.html) using a certificate and temporary credentials. Configure the SDK's credential process under the `texto` service account. This requires a trust anchor/certificate setup; it is not automatic.

An alternative is a dedicated IAM access key with only the required SES and SQS rights, held in root-managed `/etc/texto-email/aws.env` (mode 0640, group `texto`). The backend service loads this optional file:

```dotenv
AWS_ACCESS_KEY_ID=YOUR_DEDICATED_ACCESS_KEY_ID
AWS_SECRET_ACCESS_KEY=YOUR_DEDICATED_SECRET
```

Never use account-root keys or put AWS secrets in frontend environment variables. Rotate keys through your normal secret process. Keep `aws.env` out of uploads, Git and terminal output. If using an AWS profile/credential process, set `AWS_CONFIG_FILE`/`AWS_PROFILE` in that file instead.

The workload permissions should cover:

- `ses:SendEmail` for approved identity and configuration-set resources.
- Diagnostic reads: `ses:GetAccount` (account scope), `ses:GetEmailIdentity`, `ses:GetConfigurationSet`, `ses:GetConfigurationSetEventDestinations` on their supported resource scopes.
- `sqs:ReceiveMessage`, `sqs:DeleteMessage`, `sqs:ChangeMessageVisibility`, `sqs:GetQueueAttributes` for the one feedback queue.
- Required KMS rights only if you choose customer-managed KMS encryption.

Infrastructure provisioning permissions belong to the administrator, not the app role. CLI checks made with an administrator's profile do not prove that `texto` can authenticate. Restart the backend after changing service credentials.

## 8. Sending-domain DNS: do this separately from website DNS

Use a dedicated From domain to avoid disturbing existing root email:

- Staging From domain: `notify.texto-qa.in`; custom MAIL FROM: `bounce.notify.texto-qa.in`.
- Production From domain: `notify.texto.in`; custom MAIL FROM: `bounce.notify.texto.in`.

These are proposed new identities. If you already have a working verified From identity, retain it and adapt the names. SES identities and production access are regional: select **Mumbai / ap-south-1**. Retain the existing `my-first-configuration-set` if correctly configured.

1. In SES, create/inspect the domain identity `notify.texto.in` (or staging equivalent), enable Easy DKIM, and publish the exact CNAME records shown by SES. Wait for identity and DKIM success.
2. Configure its custom MAIL FROM domain in SES and publish the exact records SES provides. For this proposed production name:

| DNS name | Type | Value |
| --- | --- | --- |
| `bounce.notify.texto.in` | MX | Priority 10, `feedback-smtp.ap-south-1.amazonses.com` |
| `bounce.notify.texto.in` | TXT | `v=spf1 include:amazonses.com ~all` |
| SES-provided selectors under `notify.texto.in` | CNAME | Exact SES-provided targets |

This MAIL FROM domain is for SES feedback, not a user inbox. Follow [AWS's custom MAIL FROM requirements](https://docs.aws.amazon.com/ses/latest/dg/mail-from.html). Do not replace your existing root MX records. SES sending does not require public Postfix, inbound port 25, or changing the server's PTR to match the From domain.

3. Sign in to this app as admin, create your tenant, then in the tenant's **Domains** page add the same From domain. Publish the exact ownership TXT, application DKIM TXT, SPF and DMARC records displayed by this application, then click Verify. Application verification and SES verification are separate checks. SES CNAME selectors can coexist with the application's `texto` DKIM TXT selector.

For a fresh dedicated production domain the app records will resemble:

| DNS name | Type | Value |
| --- | --- | --- |
| `texto-verify.notify.texto.in` | TXT | `texto-domain-verification=TOKEN_FROM_APP` |
| `texto._domainkey.notify.texto.in` | TXT | Exact generated public key from app |
| `notify.texto.in` | TXT | `v=spf1 include:amazonses.com ~all` |
| `_dmarc.notify.texto.in` | TXT | `v=DMARC1; p=none` initially |

Copy the actual application values, not example tokens. The current app compares SPF against its configured expected record; the generated config sets `SPF_INCLUDE=amazonses.com`. Do not add a second SPF record at an existing name. If reusing an existing domain with other senders or a stronger DMARC policy, reconcile both the DNS and the app's expected settings; do not replace a working policy blindly. After evaluating reports/alignment, move DMARC toward quarantine/reject with matching application configuration. A real DMARC reporting mailbox/service must exist before adding `rua`.

4. Confirm sending is enabled and the account has production access in this region. Sandbox accounts restrict real recipients to verified identities. See [AWS production access](https://docs.aws.amazon.com/ses/latest/dg/request-production-access.html). Existing access in another region does not establish access here.
5. Use the admin System page SES diagnostic for the chosen identity. Check provider quota and sending limits. Start below that rate; the app's Redis limit is tenant-scoped, not a shared SES-account throttle.

## 9. Wire SES bounce/complaint feedback before customer campaigns

Use the actual existing queue if present; the queue name in an old example is not proof that it exists.

1. In `ap-south-1`, create/inspect a **Standard SNS topic**, a **Standard SQS queue**, and a dead-letter queue. Configure a redrive policy (for example, 5 failed receives), retention, and alarms for messages aging or entering the DLQ.
2. Subscribe SQS to SNS. Grant the topic `sqs:SendMessage` through the queue policy, restricted by the exact topic ARN. Follow [AWS SNS-to-SQS setup](https://docs.aws.amazon.com/sns/latest/dg/subscribe-sqs-queue-to-sns-topic.html). Enable raw message delivery for this subscription (the parser also accepts the SNS wrapper).
3. In SES configuration set `my-first-configuration-set`, enable an SNS event destination for **Bounce and Complaint only**. Grant SES publish access restricted to the configuration-set ARN/account, as described in [AWS SES event destinations](https://docs.aws.amazon.com/ses/latest/dg/event-publishing-add-event-destination-sns.html).
4. Set `SES_FEEDBACK_ENABLED=true` and the real `SES_FEEDBACK_QUEUE_URL` in `backend.env`. Restart the backend. Verify the app role can poll/delete messages.

The present consumer handles configuration-set `eventType="Bounce"` and `"Complaint"` events. Do not route Delivery/Open/Click/Send events to this same queue: unsupported events retry and then reach the DLQ. A separate destination/consumer is needed for those. Identity-level notifications can use a different payload contract; use configuration-set event publishing as described here.

## 10. Prove it works before daily use

Keep a record of these acceptance results. Merely installing packages or seeing a green health endpoint is not sufficient.

1. Both HTTPS hostnames resolve externally and certificate renewal passes.
2. Backend health is UP; PostgreSQL/RabbitMQ/Redis containers are healthy after a server reboot. Java and Next.js restart via systemd.
3. Sign in at `/admin/login` using the generated bootstrap account. Create the real tenant and assign a suitable plan/quota. Save the tenant login securely. Public self-registration remains disabled. Once the first admin exists, remove the bootstrap password from `backend.env` and restart; this does not remove the existing admin.
4. Verify the From domain in **both** SES and the app. Set `EMAIL_PUBLIC_DELIVERY_ENABLED=true`, restart the backend, then send a small controlled message to mailboxes you own. Do not start with a customer broadcast.
5. Inspect received headers for SPF, DKIM and DMARC results, From, Return-Path, and reply-to behavior. Check Gmail/Outlook delivery independently; no application can guarantee inbox placement. `DELIVERED` in this app's historical transport state indicates provider acceptance, not proof of inbox delivery.
6. Test To/Cc/Bcc, template variables, attachment upload/download, recipient content, and saved-template versions. Persistent files are under `/var/lib/texto-email/attachments`. This storage currently has **no malware scanner**; trusted-owner document testing is possible, but customer upload scanning/quarantine remains unfinished.
7. Use the [SES mailbox simulator](https://docs.aws.amazon.com/ses/latest/dg/send-an-email-from-console.html) for bounce and complaint tests. Send from the application so correlation uses a real application message. Verify SQS consumption, the corresponding record, and suppression before attempting a second send. Never create deliberate hard bounces at real customer addresses.
8. Run a tiny campaign with owned contacts: draft, audience, preview, test-send, schedule, stop/resume and report. Open its public unsubscribe URL from an unauthenticated browser; POST it and confirm future campaigns exclude that address.
9. Create a LIVE API key and verify send/idempotency. TEST API keys are blocked on SES. Test tenant isolation and revoked keys.
10. Test outbound webhooks against an endpoint you control, including signatures and retries. Do not log webhook secrets.
11. Keep billing as `noop` for manual/internal operations. If paid checkout is required, configure/validate Razorpay LIVE keys, provider-plan mappings, signed webhooks, renewals and failure/cancellation cases separately. The repository's previous validation used TEST billing; do not treat it as a certified live billing setup.
12. Restore a backup into a separate test installation and prove login, stored keys, attachments and representative records work. Integration connectors, automation and other deferred features require their own implementation/acceptance; deployment cannot complete those features.

## 11. Backups and everyday operations

```bash
sudo systemctl status texto-email-backend texto-email-frontend
sudo journalctl -u texto-email-backend -n 100 --no-pager
sudo journalctl -u texto-email-frontend -n 100 --no-pager
sudo nginx -t
sudo docker compose --env-file /etc/texto-email/dependencies.env \
  -f /opt/texto-email/infrastructure/production/dependencies.compose.yml ps
sudo bash /opt/texto-email/scripts/backup-production.sh
```

The backup script briefly stops the two apps, takes a PostgreSQL custom dump, attachment archive, secret-configuration archive and RabbitMQ definitions, then restarts previously running apps. It does **not** back up queued RabbitMQ messages, Redis state or the app binaries. Keep the exact source/release separately. Secrets and message data in backups need encryption and an off-host copy; the root-only local backup directory is not the complete backup strategy. No retention deletion or automated schedule is installed by this task.

Set up your preferred monitored nightly backup schedule with an acceptable maintenance window, encryption and retention, and test restoration regularly. Use database point-in-time recovery/managed backups if the possible loss between nightly backups is unacceptable. Monitor external website/API availability, disk space, memory, container restarts, failed/deferred mail, RabbitMQ queues/DLQs, oldest unpublished outbox events, SES reputation/quotas, SQS oldest-message age and DLQ, certificate expiry, and backup age/restore results. Assign a real alert recipient.

For updates, take a backup, stage the same release first, preserve `/etc/texto-email` and `/var/lib/texto-email`, stop the apps, rebuild and replace the JAR/frontend output, then start and recheck. Docker data stays in named volumes. Never run `docker compose down -v` against production. Don't regenerate encryption keys during an update. Database migrations may prevent a simple binary rollback; restore procedures must account for the schema version.

## 12. Move from EC2 to the physical server, then retire EC2

1. Build the same tested release on the new Ubuntu host; configure AWS authentication and test read-only SES/SQS access. Test networking and HTTPS before cutover.
2. Decide whether staging data is disposable or is the real live dataset. A fresh physical installation can start with a clean database only if you intend that; it will not contain EC2 contacts/templates/accounts.
3. For migration of real data: pause scheduling/acceptance and stop new submissions at the old ingress. Allow queued sends/retries to finish or make an explicit per-message recovery plan. Verify RabbitMQ `messages_ready`/`messages_unacknowledged`, unpublished outbox rows, scheduled campaigns and deferred messages. Some retries can take hours. Backups of broker definitions are not backups of its message contents.
4. Stop the old backend and frontend, take the final database/attachment/configuration backup, and transfer it securely. Preserve the original DKIM and webhook encryption keys. Do not run both backends against copied state: campaigns and SES sends could execute twice.
5. With the new backend stopped, restore the dump to an empty matching PostgreSQL database, restore attachment files with `texto` ownership, and use the original encryption keys and appropriate database credentials. Example restore into the NEW EMPTY target only:

```bash
sudo docker compose --env-file /etc/texto-email/dependencies.env \
  -f /opt/texto-email/infrastructure/production/dependencies.compose.yml \
  exec -T postgres sh -c 'exec pg_restore --exit-on-error --no-owner --no-acl -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < /SECURE_PATH/database.dump
```

Do not use `--clean` against a live database. Restore the attachment archive under `/var/lib/texto-email`, and restore reviewed settings rather than blindly copying old hostnames into the new environment. Redis carries temporary sessions/rate counters: plan user re-login and a quiet warm-up when moving without Redis state. Don't send a burst that bypasses already-consumed minute limits.

6. Preserve all previously issued public unsubscribe URL hostnames. If EC2 used `email-api.texto-qa.in`, old campaign messages will still link there. Keep that hostname resolving/proxying to the live backend, with a valid certificate and the public unsubscribe route working; don't strand old unsubscribe links when EC2 is retired. Keep it distinct from any later disposable staging environment.
7. Update the production email A records to the physical server, start exactly one production backend, and check actual delivery, feedback, unsubscribe and website operations from outside the network. Keep sending conservative while proving the migration.
8. Keep EC2 stopped or isolated for your agreed rollback period and verify off-site restores. Once accepted, terminate EC2 through your normal AWS process. Review retained EBS volumes, snapshots, Elastic IPs and other billable resources before deleting anything. Keep required backups and the SES/SNS/SQS resources.

Do not retire EC2 merely because the physical website loads. Successful persistence, feedback, old unsubscribe links, credentials and recovery are part of the cutover.
