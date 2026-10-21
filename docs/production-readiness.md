# Bulk campaign readiness — 19 October 2026

## Implemented and verified locally

The tenant workspace uses the Textonext logo and copies the reference header, profile control, white 288px/72px sidebar, emerald navigation, gray canvas, and sans-serif headings. Contacts, campaigns, and segments also use the directly ported Textonext DataGrid, with its green table header, column visibility controls, search, and pagination. Contacts and campaigns sort and paginate on the server; segments support column filters. Saved column visibility is scoped to the signed-in user in this browser. The application keeps its email-specific routes and APIs. This is not a claim of pixel-for-pixel parity across every Textonext screen.

- Contact create/edit/archive, search and pagination, custom JSON properties, subscription status.

- CSV field mapping, quoted commas, escaped quotes, multiline records, UTF-8 BOM, duplicate prevention, field-length validation, and bounded imports (5 MB / 10,000 records). CSV `custom.property_name` columns are preserved as contact properties.

- Saved AND/OR segments with contact/custom-field rules, numeric/date conditions, preview pagination, edit, and delete. Deeply nested saved definitions use the advanced JSON editor instead of silently flattening rules.

- Explicit campaign draft creation, reopening and editing, sender/template/inline content, whole-segment or all-subscribed audiences, individual contact selection, recipient removal, preview, and test send.

- Send, schedule/reschedule, stop/resume, clone to a new draft, live report refresh, and recipient CSV export. Cloning copies content only and requires a fresh audience and explicit send intent.

- Verified sender and content preflight. A template version is pinned at send/schedule time, so later template edits do not change a campaign halfway through dispatch.

- Database-side audience snapshots, tenant isolation, deduplication, suppression exclusion, and bounded batches of 50 recipients. Dynamic segments are evaluated when added to a campaign; a scheduled campaign retains this snapshot.

- Short dispatch claims with five-minute recovery leases, per-recipient transactions, stable per-recipient idempotency keys, and campaign row locks. Stop takes effect between acceptance transactions; already accepted email messages continue through the email queue.

- Subscription recheck at dispatch, and suppression recheck before the delivery worker submits a campaign message. Rate limits and existing quotas remain enforced by EmailService. Redis failure defers acceptance rather than allowing unlimited sending.

- Public unsubscribe links with 256-bit random tokens, SHA-256 token storage, tenant-bound/idempotent POST, a read-only GET confirmation, subscription updates, suppression records, body links, and DKIM-covered List-Unsubscribe headers. No login is required. See [RFC 8058](https://www.rfc-editor.org/rfc/rfc8058).

- Tenant/read/send permissions on contact, segment, and campaign APIs. Unsubscribe tokens are redacted in application route audits; production reverse-proxy access logs must also redact these paths.

- Recipient reports distinguish transport acceptance from confirmed delivery. The existing pipeline does not establish inbox placement; its historical email `DELIVERED` state is displayed as campaign `ACCEPTED`.

## Validation

Latest validation: **327 unit tests and 10 focused PostgreSQL integration tests passed** (337 total); frontend lint, TypeScript, and production build passed. Live HTTP checks verified contact search, omitted audience flags, audience deduplication, preview rendering, complete authenticated CSV streaming, unauthenticated export rejection, cloning, schedule/reschedule, stop/resume, repeated public unsubscribe, viewer permissions and cross-tenant hiding. The initial full Docker suite could not start its seven container-dependent test classes.

A live 1,880,041-byte CSV upload containing 10,000 records passed: one address accepted and its 9,999 duplicates skipped.

Browser checks confirmed personalized previews, sorted contacts, persisted column visibility, and segment column filtering. Screenshots: [recipient preview](tenant-email-preview.png), [Textonext grid](textonext-grid-preview.png). Server sorting/paging returned HTTP 200; unsupported sort fields returned HTTP 400.

Run frontend checks:

```powershell

cd frontend

npm run typecheck

npm run lint

npm run build

```

Run backend unit tests:

```powershell

cd backend

.\mvnw.cmd '-Dtest=*Test' test

```

Run focused PostgreSQL tests against a dedicated, disposable database (never a customer database):

```powershell

.\mvnw.cmd '-Dtest=CampaignDatabaseIT' '-Dcampaign.database.tests=true' '-Dspring.datasource.url=jdbc:postgresql://127.0.0.1:55439/campaign_tests' '-Dspring.datasource.username=texto_test' '-Dspring.datasource.password=' test

```

The focused integration suite covers Flyway migrations and Hibernate validation, JSONB segment persistence, empty contact search, tenant-scoped segment matching, suppression, deduplication, CSV import, unsubscribe, expired dispatch claims, independent recipient transactions, and report streaming. It mocks the EmailService acceptance boundary in its dispatch test; it does not exercise a real RabbitMQ/Redis/MTA handoff.

The full existing `mvnw test` suite additionally requires Docker for PostgreSQL, RabbitMQ, Redis and Postfix Testcontainers. Docker was unavailable in this session. A separate localhost-only PostgreSQL 18 cluster was used for the focused integration suite. Browser checks used a synthetic workspace in that isolated cluster, with no public email delivery. The temporary frontend, backend, and isolated PostgreSQL processes were stopped after verification; the test cluster remains under the ignored backend/target directory for repeat checks.

## Existing SES selection

The existing SES setup was selected. Its confirmed region is `ap-south-1` and configuration set is `my-first-configuration-set`; local configuration has been updated. The user confirmed the `texto-mta-01` EC2 instance profile, but this workstation has no AWS access or deployment-host access method. An admin SES diagnostic and [deployment handoff](ses-existing-setup.md) are included. SES configuration/credential failures cannot falsely suppress healthy recipients. The live SES account has not been verified in this session. The admin panel was checked in the browser against the isolated local environment; it correctly reported SES not enabled there, and tenant access to the provider diagnostic was rejected with HTTP 403.

## Production configuration still required

1. Configure production PostgreSQL, RabbitMQ and Redis with managed secrets, backups, restore checks, TLS and monitoring. Run Flyway V20–V28 as part of backend startup. Existing installations must retain migration history.

2. Set `EMAIL_PUBLIC_BASE_URL` to the public HTTPS URL of the backend. The production profile requires it. The reverse proxy must allow unauthenticated GET/POST to `/api/v1/public/unsubscribe/*` without redirects or cookie challenges. Redact tokens from access logs.

3. Configure the frontend's `NEXT_PUBLIC_API_BASE_URL` before building, and `CORS_ALLOWED_ORIGINS` on the backend.

4. Verify the existing SES production sending access, domain identity and DKIM, configuration set, permissions, feedback queue, and actual provider quotas. For Postfix, complete [production SMTP](production-smtp.md), including identity, DNS, PTR, TLS and bounce ingress.

5. Set DKIM encryption key custody, verify customer sender domains and SPF/DKIM/DMARC, and configure bounce/complaint feedback. Verify the final received message's unsubscribe headers remain DKIM-covered by the production transport, including SES.

6. Run the full queue/delivery integration suite, then perform controlled delivery and unsubscribe checks against owned test mailboxes. Public sending remains subject to `EMAIL_PUBLIC_DELIVERY_ENABLED`; configure this deliberately only after infrastructure verification.

## Remaining scope

XLS/XLSX import, a typed custom-property administration catalog, contact-list administration, provider delivery tracking beyond the existing feedback semantics, attachment storage/scanning/delivery, conditional attachments, CRM/commerce integrations, automation flows, and complete per-screen Textonext parity remain separate work. Attachment and integration schemas already existed, but their production workflows are not completed by this change.

This is an implemented and locally tested bulk-campaign workflow, not a deployed or fully certified production environment. No real subscriber campaign was sent during this work.



## Single-server deployment additions — 21 October 2026

See [the production deployment runbook](production-deployment.md). Production attachment storage can now be enabled with `EMAIL_ATTACHMENT_STORAGE_ENABLED=true` and an absolute `EMAIL_ATTACHMENT_ROOT`; it reuses the private filesystem boundary on persistent host storage. Three tests verify production wiring, root validation, and persistence across service recreation with traversal rejection. Malware scanning is still not implemented.

The repository now includes a coordinated configuration generator, loopback-only production dependencies, systemd units, HTTPS proxy configuration generation, and a maintenance-window backup script. Configuration generation, Compose parsing and Bash syntax were checked locally. The units and Nginx/Certbot steps have not been executed on the remote Ubuntu hosts. These additions do not establish live SES access, production inbox delivery, full queue compatibility, or readiness of deferred product features.
