# Texto Email Platform — Production Readiness Audit

**Repository:** `anujc08102002/texto_email`  
**Scope:** Read-only inspection of the working tree as of this audit. Latest relevant commit cited by the product team: `a117216`.  
**Constraint:** No application code was changed. Working SES / bounce / DKIM architecture is treated as valid unless this inspection found a defect. Secret values are **not** reproduced here.

**Categories**

| Tag | Meaning |
| --- | --- |
| COMPLETE | Implemented, tested (or clearly production-shaped), and suitable to operate |
| PARTIAL | Real implementation exists, but gaps remain for production scale or safety |
| MISSING | Not present in the repository |
| RISK | Present or absent in a way that can cause security, data, or operational harm |
| DEFERRED | Intentionally incomplete (UI placeholder, growth feature, or documented future work) |

Each finding includes: current implementation, relevant file/class, problem, production impact, recommended implementation, test required.

---

## Method

Inspected backend package layout, all `@RestController` endpoints, Spring Security, tenant scoping, API keys, the send/outbox/RabbitMQ/SES path, SNS/SQS feedback, bounce/complaint/suppression, domain/DKIM, billing, templates, webhooks, rate limits, idempotency, Flyway, Redis, frontend App Router, admin UI, errors/logging/metrics/health, security headers/CORS/CSRF, secrets, prod config, Docker, CI, tests, and obvious gaps vs an ESP SaaS.

This is a repository audit, not a live production-environment pentest. EC2, SES, and Gmail delivery success is accepted as operational evidence for the SES send path.

---

## 1. Backend modules and package structure

### 1.1 Modular monolith layout — COMPLETE

- **Current implementation:** Single Spring Boot 4.0.8 application (`Java 21`) with package-by-module layout under `com.texto.emailplatform`: `auth`, `tenant`, `apikey`, `plan`, `entitlement`, `subscription`, `usage`, `billing`, `domain` (+ `dkim`, `dns`), `email`, `outbox`, `queue`, `delivery` (+ `mta`), `template`, `webhook`, `suppression`, `bounce`, `complaint`, `sesfeedback`, `common`.
- **Relevant file/class:** `EmailPlatformApplication`; module packages under `backend/src/main/java/com/texto/emailplatform/`.
- **Problem:** None for current scale. `README.md` still describes several modules as “future” even though they exist.
- **Production impact:** Operators and new engineers will trust stale docs more than the code.
- **Recommended implementation:** Refresh README module table to match the tree; keep one deployable JAR.
- **Test required:** None (docs).

### 1.2 Analytics / campaigns packages — DEFERRED

- **Current implementation:** README lists `analytics`. There is no `analytics` Java package and no `campaigns` API.
- **Relevant file/class:** `README.md`; frontend `app/(dashboard)/analytics/page.tsx`, `app/(dashboard)/campaigns/page.tsx`.
- **Problem:** Product surfaces exist without backend modules.
- **Production impact:** None for transactional SES sending. Marketing/broadcast product is not shippable.
- **Recommended implementation:** Keep deferred until transactional ESP is hardened. Do not fake analytics APIs.
- **Test required:** When built: contract tests for `GET /api/v1/analytics` and campaign CRUD.

---

## 2. REST controllers and endpoints

### 2.1 Versioned tenant API surface — COMPLETE

- **Current implementation:** Controllers under `/api/v1`:
  - `AuthController` — `/auth/register|login|logout|me`
  - `ApiKeyController` — `/api-keys` CRUD-style (create/list/get/revoke)
  - `EmailController` — `/emails` list/get/send/test
  - `DomainController` — `/domains` + verify
  - `TemplateController` — templates + versions
  - `WebhookController` — configs, events, test, rotate secret
  - `SuppressionController` — list/create/delete/import
  - `PlanController` — public plans
  - `SubscriptionController`, `EntitlementController`, `UsageController`
  - `BillingController` + `RazorpayWebhookController`
  - `PlatformStatusController` — `/status`
- **Relevant file/class:** classes listed above; `SecurityConfiguration`.
- **Problem:** No `/api/v1/admin/**`, `/members`, `/campaigns`, `/analytics`, password-reset, or invites.
- **Production impact:** Tenant transactional console can operate. Platform ops and team management cannot.
- **Recommended implementation:** Freeze `/api/v1` as the customer API. Add admin and member APIs as separate authenticated surfaces.
- **Test required:** OpenAPI snapshot or controller IT listing all routes (extend `PlatformFoundationIT`).

### 2.2 Default-deny for non-API routes — COMPLETE

- **Current implementation:** After documented permit-all matchers, `/api/v1/**` requires authentication; `anyRequest().denyAll()`.
- **Relevant file/class:** `SecurityConfiguration.securityFilterChain`.
- **Problem:** None observed.
- **Production impact:** Accidental actuator/static exposure is limited (health is explicitly public).
- **Recommended implementation:** Keep deny-all; do not add wildcard permits.
- **Test required:** Existing IT that unauthenticated `/api/v1/emails` is 401.

---

## 3. Authentication and authorization

### 3.1 Dashboard sessions (hashed bearer tokens) — PARTIAL

- **Current implementation:** Register/login issue a session stored as SHA-256 of a random token (`dashboard_sessions`, 7-day TTL). `DashboardSessionAuthenticationFilter` authenticates `Authorization: Bearer`. Passwords use BCrypt. Logout deletes by hash.
- **Relevant file/class:** `AuthService`, `DashboardSessionAuthenticationFilter`, `PasswordEncoderConfiguration`, `V3__dashboard_sessions_and_unique_email.sql`.
- **Problem:** No refresh rotation, no idle timeout shorter than 7 days, no MFA, no login lockout, no password-complexity beyond length 8, no password reset. Frontend stores the raw token in `localStorage` (`frontend/lib/session.ts`).
- **Production impact:** Stolen XSS payload or shared workstation yields a full workspace session for up to 7 days. Brute-force login is unbounded.
- **Recommended implementation:** HttpOnly Secure SameSite cookies or BFF; lockout/backoff; password policy; optional MFA for owners; rotate session on login.
- **Test required:** Lockout IT; session expiry IT (already partially covered by authenticate-token path).

### 3.2 RBAC matrix exists but is inconsistently enforced — PARTIAL

- **Current implementation:** Roles `OWNER|ADMIN|MEMBER|VIEWER` and `RolePermissions`. Many services call `PermissionAuthorizationManager.requirePermission`. `EmailService.authorizeSend` checks dashboard `EMAIL_SEND` but **does not** check `EMAIL_READ` on list/get. `@EnableMethodSecurity` is on, but there are **no** `@PreAuthorize` annotations. API-key callers skip dashboard RBAC entirely.
- **Relevant file/class:** `RolePermissions`, `Permission`, `PermissionAuthorizationManager`, `EmailService.authorizeSend`.
- **Problem:** Viewer tokens that reach `POST /emails` are blocked; viewer tokens can still **list** emails if they authenticate. API keys are all-powerful within tenant send/list.
- **Production impact:** Least-privilege is incomplete. Compromised “read-only” mental model is wrong for email list vs send; API keys cannot be scoped.
- **Recommended implementation:** Centralize checks (method security or a single interceptor). Require `EMAIL_READ` on list/get. Add API-key scopes.
- **Test required:** Viewer cannot `GET /emails`; scoped API key cannot manage templates.

### 3.3 Open self-service registration — RISK

- **Current implementation:** `POST /api/v1/auth/register` is permit-all and creates a tenant + OWNER + free subscription. Frontend auth copy says workspaces are admin-provisioned; `POST /register` remains public.
- **Relevant file/class:** `AuthController.register`, `SecurityConfiguration`, `AuthService.register`.
- **Problem:** Anyone who can reach the API can create paying-path tenants and send (subject to free-plan quota).
- **Production impact:** Abuse, SES reputation burn, support load, possible billing fraud.
- **Recommended implementation:** Disable public register in `prod` (feature flag). Provision via a real admin API. Keep register only on `local`.
- **Test required:** `prod` profile returns 404/403 for `/auth/register`.

---

## 4. Tenant isolation

### 4.1 Application-level tenant scoping — PARTIAL

- **Current implementation:** `TenantContextFilter` sets `TenantContext` from `ApiKeyPrincipal` or `DashboardPrincipal`. Repositories typically use `findByIdAndTenantId` / `findByTenantId…`. Unique indexes include `tenant_id` (emails idempotency, suppressions, templates).
- **Relevant file/class:** `TenantContextFilter`, `TenantContext`, repositories across email/domain/template/webhook/suppression.
- **Problem:** Isolation is **application-enforced**, not PostgreSQL RLS. A missed `tenant_id` predicate is a cross-tenant leak. Bounce/complaint correlation by global `providerMessageId` is intentional and tenant-derived from the matched row (acceptable). Domain uniqueness is **per tenant only** (`uk_domains_tenant_domain`), so two tenants can claim the same sending domain.
- **Production impact:** Cross-tenant domain claim can steal DKIM/SPF operational ownership and confuse SES identities. A future query bug can leak PII.
- **Recommended implementation:** Unique verified domain globally (or “one verified owner”). Add integration tests that tenant A cannot `GET` tenant B IDs. Consider RLS later, not as a rewrite of SES.
- **Test required:** Cross-tenant IDOR tests for emails, domains, templates, webhooks, API keys, suppressions.

---

## 5. API key handling

### 5.1 Hashed keys, prefix, TEST/LIVE, revoke — COMPLETE

- **Current implementation:** Secrets generated as `sk_test_` / `sk_live_` + 24 random bytes, stored SHA-256, prefix retained, returned once. Entitlement limits active key count. Dashboard-only create/list/revoke.
- **Relevant file/class:** `ApiKeyService`, `HashedApiKeyAuthenticator`, `ApiKeyAuthenticationFilter`.
- **Problem:** None for basic custody. Hash is unsalted SHA-256 (acceptable for high-entropy keys).
- **Production impact:** Raw keys are not stored. Leak of DB hashes still allows offline confirmation of a stolen key.
- **Recommended implementation:** Optional HMAC/pepper from a KMS secret; keep current hashing until then.
- **Test required:** Existing SaaS core IT for create/revoke; add pepper rotation test if added.

### 5.2 Key metadata and scopes — PARTIAL

- **Current implementation:** `last_used_at` column and `ApiKeyEntity.markUsed()` exist but **are never called** from the authenticator. No IP allowlist, no per-key permission scopes, no environment gating of LIVE send vs TEST.
- **Relevant file/class:** `ApiKeyEntity.markUsed`, `HashedApiKeyAuthenticator`, `EmailService`.
- **Problem:** Operators cannot see key usage. A TEST key can hit the same send pipeline as LIVE (MTA is environment-wide, not key-scoped).
- **Production impact:** Incident response and “test vs live” isolation are weak.
- **Recommended implementation:** Update `last_used_at` asynchronously; refuse LIVE MTA for `sk_test_` keys; add scopes (`email:send`).
- **Test required:** TEST key cannot send when `MTA_IMPLEMENTATION=ses` in prod; `last_used_at` updates.

---

## 6. Admin authentication

### 6.1 Platform admin is frontend-only preview — MISSING (backend) / RISK (frontend)

- **Current implementation:** Next.js route `frontend/app/api/admin/login/route.ts` compares email/password to `ADMIN_EMAIL` / `ADMIN_PASSWORD` (defaults exist in source and `frontend/.env.example`). It returns a random `adm_…` token that is **not validated** by the Java API. Admin console screens use `lib/presentation/admin` fixtures and `previewAdminAction`. Comments state `/api/v1/admin` does not exist. Tokens live in `localStorage`.
- **Relevant file/class:** `frontend/app/api/admin/login/route.ts`, `frontend/lib/admin-session.ts`, `frontend/lib/admin/actions.ts`, `AdminPreviewBanner`.
- **Problem:** No server-side admin identity, audit log, or tenant control plane. Default credentials are in the repo. Anyone who can hit the Next server can try the login; success only unlocks a UI of mock data today, but `accounts/create` can call real `POST /api/v1/auth/register`.
- **Production impact:** If the Next app is public, default admin password is a credential. Create-account under admin UI still uses **public register**, not privileged admin. No way to suspend tenants, pull SES kill switch per tenant, or review abuse from a real API.
- **Recommended implementation:** Remove default passwords. Gate admin login on a real backend (`PLATFORM_ADMIN` users, hashed passwords, HttpOnly cookies, MFA). Do not ship the preview console to the public hostname until then. Disable register except via that API.
- **Test required:** Admin login against backend; unauthorized `/api/v1/admin/**` is 401; no default password in compiled frontend.

---

## 7. Email sending pipeline

### 7.1 Accept → persist → outbox → worker → MIME/DKIM → MTA — COMPLETE

- **Current implementation:** `POST /api/v1/emails` validates sender/template, splits suppressed vs deliverable recipients, consumes monthly quota per deliverable recipient, rate-limits per accepted message, persists `QUEUED`, writes `EMAIL_DELIVERY_REQUESTED`, emits webhook `email.queued`. `EmailDeliveryProcessor` drives state machine, bounce token, `DeliveryEngine` / `SmtpDeliveryEngine`, SES or SMTP MTA.
- **Relevant file/class:** `EmailService`, `EmailDeliveryProcessor`, `EmailDeliveryWorker`, `SmtpDeliveryEngine`, `SesMtaClient`.
- **Problem:** `DELIVERED` means provider accepted the message (SES `250` / message id), not inbox placement. That is documented and consistent with SES.
- **Production impact:** Correct for an ESP handoff model. Support must not treat `DELIVERED` as Gmail inbox.
- **Recommended implementation:** Keep semantics; expose provider id in UI (already on responses).
- **Test required:** Existing `Phase5EmailPipelineIT`, `SesMtaClientTest`.

### 7.2 Attachments, scheduling, CC-as-separate-messages — DEFERRED / PARTIAL

- **Current implementation:** MIME composer supports text/html alternative. No attachments API. One DB row per send with JSON recipient arrays (not per-recipient messages).
- **Relevant file/class:** `MimeMessageComposer`, `EmailMessageEntity`.
- **Problem:** Cannot send invoices/PDFs; per-recipient bounce is tracked via arrays, not child rows.
- **Production impact:** Transactional text/html mail works. Attachment-heavy product does not.
- **Recommended implementation:** Defer attachments until signed URL upload + size limits. Do not split the working one-row model without a migration plan.
- **Test required:** Attachment size/type tests when added.

---

## 8. Outbox

### 8.1 Transactional outbox publisher — PARTIAL

- **Current implementation:** `OutboxService.enqueue` in the send transaction. `OutboxPublisher` `@Scheduled` polls unpublished rows (batch 50), publishes to RabbitMQ, marks published or records failure.
- **Relevant file/class:** `OutboxService`, `OutboxPublisher`, `OutboxEventRepository.findUnpublished`, `V7__phase5_email_delivery_pipeline.sql`.
- **Problem:** Query has **no `FOR UPDATE SKIP LOCKED`**. Dual app instances (or a stuck poller + a new deploy) can double-publish. No publisher confirms. Unknown event types are logged and left unpublished forever if not marked (actually it still tries `publishEvent` then may fail). Failures retry every poll with no backoff cap.
- **Production impact:** Duplicate delivery jobs are **mostly** idempotent in `EmailDeliveryProcessor` (terminal/in-progress guards), but duplicate Rabbit messages still cause extra work. Dual-EC2 without locking is unsafe.
- **Recommended implementation:** Native query `FOR UPDATE SKIP LOCKED`; exponential backoff; optional `published_at` uniqueness. Keep single-writer until then.
- **Test required:** Concurrent publisher IT with two threads; duplicate job does not double-send.

---

## 9. RabbitMQ

### 9.1 Delivery topology with TTL retries and DLQ — COMPLETE

- **Current implementation:** Durable direct exchange, delivery queue with DLX, retry queues (30s, 2m, 10m, 30m, 2h) dead-lettering back to delivery, DLQ. Manual ack, prefetch 10, `defaultRequeueRejected=false`. Bounce/complaint/webhook queues also exist.
- **Relevant file/class:** `EmailDeliveryQueueConfiguration`, `EmailDeliveryQueues`, `EmailQueues`, `WebhookDeliveryWorker`.
- **Problem:** No publisher confirms, no TLS/AMQPS in config, compose uses default user/password published only on `127.0.0.1`. Worker `basicNack(..., requeue=false)` sends failures to DLQ immediately rather than retrying poison JSON.
- **Production impact:** Malformed jobs go to DLQ (good). Broker credentials on EC2 must not use compose defaults. Message loss possible if broker accepts publish then dies before persist without confirms (rare with durable queues if publisher waits).
- **Recommended implementation:** Enable publisher confirms + returns; AMQPS or VPC-only; unique prod credentials; alert on DLQ depth.
- **Test required:** Existing pipeline IT; add confirm-mandatory test if enabled.

---

## 10. SES

### 10.1 SESv2 RAW submission — COMPLETE

- **Current implementation:** `SesMtaClient` sends already-composed RFC 822 as SESv2 `RawMessage`, optional configuration set, maps throttling/5xx to temporary failure and 4xx/identity errors to permanent. Production validator allows `implementation=ses` and skips Postfix identity requirements. Credentials: AWS SDK default chain. Public delivery still gated by `PublicDeliveryGuard`.
- **Relevant file/class:** `SesMtaClient`, `MtaClientConfiguration`, `MtaPropertiesValidator`, `docs/mta-transport.md`.
- **Problem:** No in-app SES identity / DKIM provisioning (CreateEmailIdentity). Customer domain verification in **this** app is DNS TXT for Texto’s DKIM/SPF/DMARC, not AWS identity APIs. That is not a defect in the send path if identities are provisioned operationally (Gmail test succeeded).
- **Production impact:** New customer domains will fail at SES (`MessageRejected` / not verified) until an operator verifies the identity in AWS. App will correctly mark those sends permanently failed.
- **Recommended implementation:** Keep RAW + app-side DKIM. Add a **runbook** (not a redesign) for SES domain identity + mail-from + configuration set. Optionally automate identity create later.
- **Test required:** Existing `SesMtaClientTest`; contract test for configuration set header.

### 10.2 SES vs Postfix docs — PARTIAL

- **Current implementation:** `docs/production-smtp.md` still frames public MX/Postfix as not ready. SES is the live public path.
- **Relevant file/class:** `docs/production-smtp.md`, `docs/mta-transport.md`.
- **Problem:** Operators may enable Postfix public delivery by mistake.
- **Production impact:** Split-brain sending reputation if both paths are on.
- **Recommended implementation:** Document SES as the production MTA; keep Postfix as local/controlled. Do not enable Postfix public MX without a dedicated project.
- **Test required:** None.

---

## 11. SNS/SQS feedback

### 11.1 SQS poller → parse → bounce/complaint ingest — COMPLETE

- **Current implementation:** `SesFeedbackConsumer` long-polls SQS when `email-platform.ses-feedback.enabled=true`. Parses SNS envelope or raw SES event. Control messages (subscription/unsubscribe) are deleted. Success deletes. Unresolved/unprocessable leave the message for SQS retry/redrive.
- **Relevant file/class:** `SesFeedbackConsumer`, `SesFeedbackParser`, `SesFeedbackAwsConfiguration`.
- **Problem:** No SNS cryptographic signature verification (trusts SQS body). `application-prod.yml` defaults `enabled: false` — production must set env. Visibility timeout 120s vs processing time should be monitored.
- **Production impact:** If the SQS queue policy is locked to SNS/account, signature verification is defense in depth, not a blocker. If the queue is mis-permissioned, forged bounce/complaint events could suppress recipients. Disabled flag means **no SES bounces applied**.
- **Recommended implementation:** Keep SQS consumer. Enforce IAM: only SNS can `SendMessage`. Enable feedback in prod env. Add signature verification later if messages are ever accepted from HTTP SNS.
- **Test required:** Existing `SesFeedbackConsumerTest`, parser tests. Add IAM/policy review (ops).

---

## 12. Bounce processing

### 12.1 SES bounce path (provider message id + recipient) — COMPLETE

- **Current implementation:** `SesBounceIngestionService` matches unique `providerMessageId`, requires recipient on the message, hashes events for idempotency, uses `FeedbackAuthorization.SES` so mutation is explicit and not confused with DSN tokens. Policy: soft bounce defer, hard bounce suppress.
- **Relevant file/class:** `SesBounceIngestionService`, `BounceApplicationService`, `BouncePolicy`, `BounceEventHash`.
- **Problem:** Ambiguous provider ids throw unresolved (left on SQS). Uncorrelated events retry — good. Docs still say public DSN MX is off — correct; SES path is the production bounce path.
- **Production impact:** Working model for SES. Do not replace with public MX.
- **Recommended implementation:** None architecturally. Alert on `uncorrelated_bounces_total` and SQS DLQ.
- **Test required:** Existing `SesBounceIngestionServiceTest`, `BouncePolicyTest`.

### 12.2 Local Postfix DSN path — COMPLETE (non-prod) / DEFERRED (public MX)

- **Current implementation:** Trusted DSN ingress, token VERP, Rabbit bounce worker, optional maildir poller. Not a public listener.
- **Relevant file/class:** `TrustedDsnIngressService`, `DsnMailboxPoller`, `docs/bounces.md`.
- **Problem:** Public bounce MX is not implemented (intentional).
- **Production impact:** None while SES feedback is on.
- **Recommended implementation:** Leave deferred. Do not build public MX to “replace” SES.
- **Test required:** Existing DSN tests.

---

## 13. Complaint processing

### 13.1 SES complaint ingest + suppression — COMPLETE

- **Current implementation:** `SesComplaintIngestionService` + `ComplaintApplicationService` persist events, correlate, suppress as `COMPLAINT` without flipping message to bounced. Trusted local complaint ingress exists for tests.
- **Relevant file/class:** `SesComplaintIngestionService`, `ComplaintIngestionService`, `ComplaintPolicy`, `docs/complaints.md`.
- **Problem:** No ISP FBL (Gmail/Yahoo) besides SES. That is expected on SES.
- **Production impact:** SES complaints are handled. Direct ISP FBLs are not required if all mail goes through SES.
- **Recommended implementation:** Keep SES as the complaint source. Do not add a public FBL listener.
- **Test required:** Existing complaint ITs/unit tests.

---

## 14. Suppression

### 14.1 Tenant suppression list with bounce/complaint types — COMPLETE

- **Current implementation:** Unique `(tenant_id, normalized_email)`, CRUD + import API, send path excludes suppressed recipients from quota and delivery. Bounce/complaint processors upsert.
- **Relevant file/class:** `SuppressionService`, `SuppressionController`, `V6` unique index.
- **Problem:** No global/platform suppression list (spamtrap, legal). Delete allows tenants to re-enable an address that SES complained about (tenant-level).
- **Production impact:** Re-sending after complaint can hurt SES account reputation for **all** tenants.
- **Recommended implementation:** Platform-level complaint/abuse suppressions that tenants cannot delete. Keep tenant list for their own unsubscribes.
- **Test required:** Existing upsert test; add “tenant cannot delete COMPLAINT from SES” when policy is added.

---

## 15. Domain verification

### 15.1 DNS TXT ownership, SPF, DKIM, DMARC checks — PARTIAL

- **Current implementation:** On create, records generated; `POST /verify` looks up TXT with timeout; rate-limited per tenant. SPF check is presence/match, not recursive RFC 7208 evaluation (documented). Platform test senders optional (`allow-platform-test-senders`, false in prod yml).
- **Relevant file/class:** `DomainVerificationService`, `DomainService`, `DomainVerificationRateLimiter`.
- **Problem:** Manual verify only (no scheduler). Two tenants may register the same domain. SPF include `_spf.texto.email` may be unpublished (`spf-include-provisioned` default false). SES identity not created automatically (see 10.1).
- **Production impact:** Customers can be “VERIFIED” in-app and still fail SES. Shared-domain claim is an account-takeover adjacent issue.
- **Recommended implementation:** Global unique verified domains; periodic re-verify job; runbook linking app VERIFIED ↔ SES identity VERIFIED.
- **Test required:** Existing DNS tests; add unique-domain conflict IT.

---

## 16. DKIM

### 16.1 App-side rsa-sha256 signing + encrypted key custody — COMPLETE

- **Current implementation:** Keys generated per domain, AES-GCM custody, `DKIM_KEY_ENCRYPTION_KEY` required in `prod` (`DkimKeyEncryptionValidator`). Signer is relaxed/relaxed rsa-sha256. Private keys never returned on verification API.
- **Relevant file/class:** `DkimKeyService`, `DkimKeyProtector`, `DkimSigner`, `DkimSigningService`, `V15__dkim_key_custody.sql`, `docs/dkim-key-custody.md`.
- **Problem:** Single active key per domain; no scheduled rotation UI. SES Easy DKIM may **also** sign; dual signatures are valid and should not be “fixed” without evidence of auth failure.
- **Production impact:** Custody is production-shaped. Missing rotation is operational debt, not a send blocker.
- **Recommended implementation:** Document rotation procedure; optional second selector later.
- **Test required:** Existing `DkimSignerTest`, `DkimKeyProtectorTest`, custody tests.

---

## 17. Billing

### 17.1 Razorpay checkout + signed webhooks + plan changes — PARTIAL

- **Current implementation:** Provider SPI (`noop` / `razorpay`). Checkout, cancel, pause, resume, change-plan. Webhook signature verified; provider event id de-duplicated; grace period. `GET /billing/config` is public and must not leak secrets (by design).
- **Relevant file/class:** `BillingController`, `RazorpayWebhookController`, `BillingWebhookService`, `RazorpayBillingProvider`.
- **Problem:** Default provider is `noop`. Prod yml does not force `razorpay`. `.env.example` contains what appear to be **live Razorpay credentials** (values not copied here). No dunning UI beyond admin preview. No invoices/tax/GST e-invoicing.
- **Production impact:** If prod still uses `noop`, paid plans never activate from Razorpay. If example credentials are real, they are compromised via git history.
- **Recommended implementation:** Rotate any credentials that were committed. Scrub `.env.example` to empty placeholders. Require `BILLING_PROVIDER` explicitly in prod. Add invoice export later.
- **Test required:** Existing `RazorpayWebhookSignatureTest`, `BillingCheckoutServiceTest`.

---

## 18. Templates

### 18.1 Versioned templates + safe placeholder renderer — COMPLETE

- **Current implementation:** Version table, activate version, archive, schema-gated `{{var}}` replacement, no expressions.
- **Relevant file/class:** `TemplateService`, `TemplateRenderer`, `TemplateController`.
- **Problem:** HTML is not sanitized (tenants inject their own HTML by design). No MJML, no visual builder.
- **Production impact:** A tenant can send XSS to **recipients**, not to your dashboard (email HTML). Acceptable for an ESP; warn in UI.
- **Recommended implementation:** Optional HTML sanitizer for dashboard preview only; keep raw HTML for send.
- **Test required:** Existing `TemplateRendererTest`; Phase4 IT.

---

## 19. Webhooks

### 19.1 Signed outbound webhooks with SSRF checks — PARTIAL

- **Current implementation:** HTTPS-only (HTTP local), block localhost/link-local/site-local/metadata, HMAC signature headers, AES-GCM secret custody, Rabbit worker, backoff, DLQ, rotate secret, test event.
- **Relevant file/class:** `WebhookService`, `WebhookUrlValidator`, `WebhookDeliveryWorker`, `WebhookSecretProtector`, `WebhookSigner`.
- **Problem:** SSRF check is at **create** time, not at **delivery** (DNS rebinding). `WEBHOOKS_ENCRYPTION_KEY` is **not** fail-fast required in `prod` (unlike DKIM); missing key uses a hardcoded local derivation — **cannot decrypt across instances / after restart if mixed**. Worker nacks to DLQ on unexpected errors. Retry of “too early” events re-publishes immediately (busy loop risk).
- **Production impact:** Secret custody can break webhook signing after deploy if the env key was missing at first write. SSRF rebinding can hit cloud metadata from EC2 if a hostname later points to `169.254.169.254`.
- **Recommended implementation:** Require encryption key in prod (same pattern as DKIM). Re-resolve DNS and block private IPs at delivery. Use delayed retry queues (already have TTL pattern for email).
- **Test required:** Existing URL validator tests; add delivery-time private IP test; prod missing-key startup test.

---

## 20. Rate limiting

### 20.1 Redis send limit + domain verify limit — PARTIAL

- **Current implementation:** Tenant message-per-UTC-minute limit via Redis Lua, fail-closed, refund on rollback. Domain verify per-minute limiter. Prod default 830/min. Bounds validated at startup (cannot set 0).
- **Relevant file/class:** `EmailSendRateLimiter`, `EmailSendRateLimitValidator`, `DomainVerificationRateLimiter`, `docs/email-send-rate-limit.md`.
- **Problem:** No login/register/API-wide HTTP rate limit. No per-key limit. No SES account-level pacing beyond treating throttling as retry.
- **Production impact:** Credential stuffing and register storms are unmetered. Send abuse is bounded per tenant.
- **Recommended implementation:** Gateway or Spring filter for `/auth/*` and `/api/v1/**` by IP. Keep Redis send limiter.
- **Test required:** Existing Redis IT and fail-closed test; add auth limiter IT.

---

## 21. Idempotency

### 21.1 Send Idempotency-Key — COMPLETE

- **Current implementation:** Optional header, unique `(tenant_id, idempotency_key)`, race handled via `DataIntegrityViolationException` refetch. Bounce/complaint/billing events hashed or provider-id unique.
- **Relevant file/class:** `EmailService`, `V7` unique index, `BounceEventHash`, `ComplaintEventHash`, `BillingWebhookService`.
- **Problem:** Key truncated to 128 chars silently. Only send is idempotent; other POSTs are not.
- **Production impact:** Client retries on send are safe. Checkout retries may create duplicate Razorpay orders.
- **Recommended implementation:** Idempotency for checkout; document truncation.
- **Test required:** Existing `Phase5EmailPipelineIT.idempotencyKeyReturnsSameMessage`.

---

## 22. Database schema and Flyway

### 22.1 Flyway-owned schema, Hibernate validate — COMPLETE

- **Current implementation:** `V1`–`V16` migrations, `ddl-auto: validate`, UTC, FKs, check constraints, tenant indexes.
- **Relevant file/class:** `backend/src/main/resources/db/migration/*.sql`, `application.yml`.
- **Problem:** Legacy tables evolved in place (rename sending_domains → domains). No RLS. Some bounce `tenant_id` nullable for unmatched events (intentional).
- **Production impact:** Schema is coherent. Backup/restore and migration rehearsal on a prod-sized DB are not in-repo.
- **Recommended implementation:** Backup job, restore drill, `flyway repair` runbook. Do not rewrite V1–V16.
- **Test required:** ITs already run migrations via Testcontainers.

---

## 23. Redis usage

### 23.1 Rate limit + health + AOF in compose — PARTIAL

- **Current implementation:** `StringRedisTemplate` Lua limiter; health indicator; compose Redis with AOF, no password, bound `127.0.0.1`. Prod yml sets host/port only — **no `REDIS_PASSWORD`**.
- **Relevant file/class:** `application.yml`, `application-prod.yml`, `infrastructure/docker-compose.yml`, `EmailSendRateLimiter`.
- **Problem:** Unauthenticated Redis. Fail-closed send is good; a public Redis would be catastrophic.
- **Production impact:** On a locked-down EC2 VPC this may be acceptable. Any network exposure allows limiter bypass or data write.
- **Recommended implementation:** AUTH + TLS or IAM (ElastiCache); require password in prod properties.
- **Test required:** Existing fail-closed tests; startup fail if prod password missing.

---

## 24. Frontend routes/pages/components

### 24.1 App Router tenant console — PARTIAL

- **Current implementation:** Next.js 16 App Router. Auth: login/register. Dashboard: emails (list/new/detail), templates, domains, api-keys, webhooks, suppressions, billing, plans, settings. Marketing home. Services under `frontend/services/*` call `/api/v1`.
- **Relevant file/class:** `frontend/app/**`, `frontend/services/**`.
- **Problem:** Analytics and campaigns are placeholders. Settings notifications are disabled. No middleware.ts. Client-only auth gate (`AppShell` redirects if `localStorage` user missing) — tokens not httpOnly; deep links flash empty.
- **Production impact:** Core operator workflows exist. SEO/marketing pages are fine. Auth UX is SPA-grade, not cookie-session-grade.
- **Recommended implementation:** Next middleware for session cookie once backend supports it. Hide campaigns/analytics or label preview (already labeled).
- **Test required:** No frontend tests exist (see §38). Playwright for login → send email.

---

## 25. Existing dashboard functionality

### 25.1 Wired to live APIs — COMPLETE (transactional subset)

- **Current implementation:** Emails, templates, domains, API keys, webhooks, suppressions, billing/plans consume backend services. Dashboard home hits public status.
- **Relevant file/class:** `frontend/services/emails.ts` (+ idempotency key in composer), domains, templates, webhooks, suppressions, billing, api-keys.
- **Problem:** Home metrics/analytics charts use presentation fixtures (`lib/presentation`).
- **Production impact:** Users may believe volume charts are real.
- **Recommended implementation:** Drive dashboard metrics from `GET /usage` + email counts until analytics exists. Label remaining fixtures.
- **Test required:** Component/E2E that composer send hits API.

---

## 26. Existing admin functionality

### 26.1 Preview console — DEFERRED / RISK

- **Current implementation:** Full IA (tenants, billing ops, compliance, audit, platform flags, messages, insights) on **static preview data**. Create account page can register real tenants. Login is local Next route.
- **Relevant file/class:** `frontend/app/admin/**`, `frontend/lib/presentation/admin.ts`.
- **Problem:** Looks like a control plane; is not.
- **Production impact:** False operational confidence; accidental real tenant creation; credential defaults (see §6).
- **Recommended implementation:** Do not expose `/admin` on the production hostname until backend admin exists. Or compile it out.
- **Test required:** Production build must not contain default admin password.

---

## 27. Error handling

### 27.1 JSON envelope + global handler — COMPLETE

- **Current implementation:** `ApiResponse` success/failure; `ApiException` with codes; validation/malformed/401/403/404/500. Spring `server.error.include-*` never. Unhandled exceptions logged with request id, generic client message.
- **Relevant file/class:** `GlobalExceptionHandler`, `ApiException`, `application.yml` `server.error`.
- **Problem:** Some worker `exception.getMessage()` may land in webhook event error fields (customer-visible diagnostic).
- **Production impact:** Low. Avoid leaking hostnames from DNS exceptions into webhook payloads (mostly already generic).
- **Recommended implementation:** Keep; map webhook delivery errors to stable codes.
- **Test required:** Existing `GlobalExceptionHandlerTest`.

---

## 28. Logging

### 28.1 ECS JSON logs + correlation id + masking helper — PARTIAL

- **Current implementation:** `logging.structured.format.console: ecs`. `CorrelationIdFilter` sets MDC and `X-Request-Id` (accepts inbound header ≤128 chars). `SensitiveDataMasker` exists for key/value masking.
- **Relevant file/class:** `CorrelationIdFilter`, `SensitiveDataMasker`, `application.yml` logging.
- **Problem:** Masker is not wired as a global log encoder filter — developers must call it. Inbound `X-Request-Id` can pollute correlation (log injection / collision). Email worker logs on failure without message id in the catch of `EmailDeliveryWorker` (“failed to process job”).
- **Production impact:** Incident correlation across API → worker is weaker than it should be. Secrets in ad-hoc logs possible.
- **Recommended implementation:** Generate request id always; echo inbound as `X-Incoming-Request-Id`. JSON encoder field mask. Include `messageId` in worker warn.
- **Test required:** Existing masker test; filter test that oversize header is replaced.

---

## 29. Metrics

### 29.1 Micrometer counters, no scrape endpoint — PARTIAL

- **Current implementation:** Counters for bounce, complaint, send rate-limit. Actuator `exposure.include: health` only — **no Prometheus/OTLP**.
- **Relevant file/class:** `BounceMetrics`, `ComplaintMetrics`, `EmailSendRateLimitMetrics`, `application.yml` `management`.
- **Problem:** Metrics exist in-process but cannot be scraped in prod with current config.
- **Production impact:** Blind on queue depth (except Rabbit UI), bounce rates, SES throttling, outbox lag.
- **Recommended implementation:** Expose `prometheus` on a private port/network, or push CloudWatch. Metric for outbox unpublished count, SQS age, DLQ.
- **Test required:** Actuator security IT: prometheus not public without auth.

---

## 30. Health checks

### 30.1 Liveness/readiness + dependency + MTA diagnostic — COMPLETE

- **Current implementation:** Probes enabled. Health: db, rabbit, redis, custom MTA indicator + production readiness fields. Prod `show-details: never`, `show-components: when-authorized`. Disk space disabled.
- **Relevant file/class:** `application.yml` / `application-prod.yml` `management`, `MtaHealthIndicator`, `ProductionReadinessAssessor`.
- **Problem:** `when-authorized` has no actuator users configured — components likely hidden (safe). MTA health for SES may still probe SMTP settings depending on implementation (verify in ops). `/api/v1/status` is public and always `ok`.
- **Production impact:** Load balancer can use `/actuator/health`. Status endpoint is not a deep health check (fine).
- **Recommended implementation:** Map `health/liveness` and `health/readiness` separately in ALB. Do not expose details publicly (already).
- **Test required:** Existing `PlatformFoundationIT` health test.

---

## 31. Security headers

### 31.1 No CSP / HSTS / frame-options on API or Next — MISSING

- **Current implementation:** Spring Security chain does not configure headers. `frontend/next.config.ts` has no `headers()`.
- **Relevant file/class:** `SecurityConfiguration`, `frontend/next.config.ts`.
- **Problem:** Missing `X-Content-Type-Options`, `Referrer-Policy`, `X-Frame-Options`/`frame-ancestors`, CSP, HSTS (HSTS usually at ALB).
- **Production impact:** Clickjacking of dashboard, MIME sniffing, weaker XSS mitigation (especially with `localStorage` tokens).
- **Recommended implementation:** Spring `headers.defaults` + Next `headers()` + ALB HSTS.
- **Test required:** IT asserting security headers on `/api/v1/status` and `/`.

---

## 32. CORS

### 32.1 Explicit origins, credentials, needed headers — COMPLETE

- **Current implementation:** Origins from `CORS_ALLOWED_ORIGINS` (required in prod yml). Methods + `Authorization`, `X-Api-Key`, `Idempotency-Key`. Credentials true. Prod must set the env (no localhost default in prod file).
- **Relevant file/class:** `SecurityConfiguration.corsConfigurationSource`, `application-prod.yml`.
- **Problem:** If ops set `*` with credentials, browsers reject — or a mistaken extra origin expands CSRF-like cross-site reads. Comma-separated list does not support origin patterns.
- **Production impact:** Low if env is a single dashboard origin.
- **Recommended implementation:** Fail startup on blank/`*` in prod. Keep allow-list.
- **Test required:** Existing CORS IT for Idempotency-Key.

---

## 33. CSRF considerations

### 33.1 CSRF disabled for bearer API — PARTIAL (appropriate) / RISK (token storage)

- **Current implementation:** `csrf.disable()` with stateless sessions. Dashboard uses `Authorization` header, not cookies. Razorpay webhook is unauthenticated except signature.
- **Relevant file/class:** `SecurityConfiguration`, `frontend/lib/session.ts`.
- **Problem:** Classic CSRF is limited without cookies. XSS becomes the main issue because the bearer token is in `localStorage`. Cookie migration would **require** CSRF tokens or SameSite strict.
- **Production impact:** XSS in Next app = full API access. Admin token same.
- **Recommended implementation:** Keep CSRF off until cookie auth. Prioritize XSS hardening (CSP) and stop putting tokens in `localStorage`.
- **Test required:** CORS + credentialed cross-origin POST without token is 401.

---

## 34. Secrets handling

### 34.1 Prod YAML uses env placeholders — COMPLETE (pattern) / RISK (examples)

- **Current implementation:** `application-prod.yml` has no passwords. DKIM key required at startup. AWS uses default credential chain (instance role — correct for EC2). `.gitignore` ignores `.env` but **not** `.env.example`.
- **Relevant file/class:** `application-prod.yml`, `.env.example`, `frontend/.env.example`, `DkimKeyEncryptionValidator`.
- **Problem:** `.env.example` includes Razorpay live-looking key id **and secret**. Frontend example includes a default admin password. Webhook encryption key not required in prod. Redis/Rabbit passwords not required complexity.
- **Production impact:** Credential leak if those Razorpay values are real (rotate immediately). Default admin password in git.
- **Recommended implementation:** Replace example secrets with empty strings. Rotate Razorpay, webhook, and admin credentials. Use SSM/Secrets Manager on EC2. Require `WEBHOOKS_ENCRYPTION_KEY` in prod.
- **Test required:** CI grep that `.env.example` has empty `*_SECRET` values.

**Do not commit new secrets. Rotate anything that was ever in git.**

---

## 35. Production configuration

### 35.1 `application-prod.yml` fail-open on several flags — PARTIAL

- **Current implementation:** Datasource/Rabbit/Redis/CORS required via `${VAR}` (fail if missing). SES and SES feedback default **false**. `EMAIL_PUBLIC_DELIVERY_ENABLED` default false. `ALLOW_PLATFORM_TEST_SENDERS` false. Swagger denied. STARTTLS required for Postfix path.
- **Relevant file/class:** `application-prod.yml`, `MtaPropertiesValidator`.
- **Problem:** A `prod` profile process can boot with SES disabled or feedback disabled and silently not send / not process bounces. No `spring.rabbitmq.ssl`. No datasource SSL. Billing provider not forced.
- **Production impact:** Mis-set env is the main outage mode. Public delivery kill switch default off is **safe**.
- **Recommended implementation:** Startup validation: if `mta.implementation=ses` then `ses.enabled` and `ses-feedback.enabled` and configuration set name required. JDBC `sslmode=require`.
- **Test required:** Validator tests already cover MTA; extend for SES feedback required-in-prod.

---

## 36. Docker / infrastructure files

### 36.1 Local compose only — PARTIAL

- **Current implementation:** `infrastructure/docker-compose.yml` for Postgres 16, RabbitMQ, Redis, Mailpit, Postfix (public delivery hard-off). Postfix Dockerfile + entrypoint. Ports bound to `127.0.0.1`. No Dockerfile for backend or frontend. No Terraform/Ansible/user-data in repo.
- **Relevant file/class:** `infrastructure/docker-compose.yml`, `infrastructure/postfix/*`.
- **Problem:** Production EC2 image/build is out of band. Drift between local Postfix and prod SES is expected.
- **Production impact:** Reproducible prod deploys are not encoded. Recovery depends on tribal knowledge.
- **Recommended implementation:** App Dockerfiles + compose `prod` overlay **or** documented AMI/systemd units in-repo. Do not publish Postfix 25 publicly.
- **Test required:** Compose config validate in CI.

---

## 37. CI/CD

### 37.1 No GitHub Actions / pipeline in repo — MISSING

- **Current implementation:** Maven wrapper + `npm` scripts. No `.github/workflows`. Surefire runs `*Test` and `*IT`.
- **Relevant file/class:** `backend/pom.xml`, `frontend/package.json`.
- **Problem:** Nothing enforces tests, image build, secret scan, or deploy on push.
- **Production impact:** Broken main can ship to EC2 manually.
- **Recommended implementation:** CI: `mvn test`, `npm run lint && npm run typecheck && npm run build`, secret scan, Flyway info. CD: explicit approval to EC2.
- **Test required:** Workflow itself (smoke).

---

## 38. Tests

### 38.1 Strong backend tests, no frontend tests — PARTIAL

- **Current implementation:** ~67 Java test classes including Testcontainers ITs (foundation, SaaS core, Phase4/5, Postfix MTA, Redis limiter) and focused unit tests for SES feedback, DKIM, bounce, complaint, billing signature, webhooks.
- **Relevant file/class:** `backend/src/test/java/**`.
- **Problem:** Zero frontend unit/E2E tests. No load test. No chaos test for SQS/outbox dual-writer.
- **Production impact:** Regression risk is on UI and on multi-instance ops, not on the core Java pipelines that are well tested.
- **Recommended implementation:** Playwright happy path; keep Testcontainers ITs in CI (they need Docker).
- **Test required:** CI must run `*IT` with Docker.

---

## 39. Missing production features

### 39.1 Control plane and product gaps — MISSING / DEFERRED

| Feature | Category | Notes |
| --- | --- | --- |
| Platform admin API | MISSING | Tenant suspend, impersonation, flags, audit |
| Member invite / RBAC UI | MISSING | Permissions exist; no `/members` |
| Password reset / email verify | MISSING | |
| MFA | MISSING | |
| HTTP auth rate limits | MISSING | |
| Observability scrape | MISSING | Prometheus/OTLP |
| Security headers / CSP | MISSING | |
| App Docker + CI | MISSING | |
| Analytics / opens / clicks | DEFERRED | No tracking pixel |
| Campaigns / lists / segments | DEFERRED | |
| Attachments | DEFERRED | |
| Inbound parse / replies | DEFERRED | |
| List-Unsubscribe headers | MISSING | One-click unsubscribe expected by Gmail for bulk |
| Per-tenant sending domains in SES automation | MISSING | Ops runbook instead |
| Backups, WAF, secret manager | MISSING in repo | May exist on EC2 outside git |

- **Recommended implementation:** Prioritize blockers in section A. Defer campaigns/analytics.
- **Test required:** Per feature when added.

---

## 40. Known technical debt

### 40.1 Documented and observed debt — RISK / PARTIAL

- **Current implementation:** README outdated vs code. Dual Jackson `ObjectMapper` bean in `EmailPlatformConfiguration` vs Spring Boot 4 defaults. `@EnableMethodSecurity` unused. `EmailQueues.OUTBOUND_QUEUE` may be leftover vs `EmailDeliveryQueues`. Webhook secret `sha256` computed and discarded in `WebhookService.create`. `markUsed()` dead. Public register vs admin copy mismatch. Postfix-oriented readiness assessor still used when MTA is SES (diagnostic noise).
- **Relevant file/class:** `README.md`, `EmailPlatformConfiguration`, `EmailQueues`, `WebhookService`, `ProductionReadinessAssessor`.
- **Problem:** Debt increases misconfiguration risk more than SES-path defects.
- **Production impact:** Wrong health interpretation; unused topology; confusion during incidents.
- **Recommended implementation:** Cleanup pass **without** changing SES/bounce/DKIM behavior.
- **Test required:** Health JSON contract for `implementation=ses`.

---

## Priority lists

### A. Critical blockers

1. **Treat committed credential material as compromised.** `.env.example` contains Razorpay live-looking secrets; rotate keys in Razorpay, scrub the file to empty placeholders, and purge from history if they were real. Do not print values in tickets.
2. **Remove default admin password from frontend source and example env.** Disable or auth-gate `/admin` and `/api/admin/login` on the production hostname.
3. **Close public `POST /api/v1/auth/register` in production** (or put it behind the future admin API). Open signup plus SES is an abuse and reputation blocker.
4. **Confirm production env:** `MTA_IMPLEMENTATION=ses`, `SES_ENABLED=true`, `EMAIL_PUBLIC_DELIVERY_ENABLED` as intended, `SES_FEEDBACK_ENABLED=true`, `SES_FEEDBACK_QUEUE_URL` set, configuration set name set, `DKIM_KEY_ENCRYPTION_KEY` set, `CORS_ALLOWED_ORIGINS` = real dashboard origin only, `BILLING_PROVIDER` explicit. Feedback disabled means bounces/complaints will not apply.
5. **Rotate and set `WEBHOOKS_ENCRYPTION_KEY` in prod** before creating production webhooks (startup should fail if missing).

### B. High-priority work

1. Platform admin backend: suspend tenant, disable send, audit log, no mock console.
2. Login/register rate limits and lockout.
3. HttpOnly session cookies (or BFF) + CSP/security headers; stop `localStorage` tokens.
4. Global unique verified sending domains; tenant cannot delete SES `COMPLAINT` suppressions.
5. Outbox `SKIP LOCKED`; Redis AUTH; Rabbit MQ production credentials/TLS or VPC-only.
6. Prometheus/CloudWatch: outbox lag, DLQ, bounce/complaint, SES errors, SQS age.
7. CI: backend ITs + frontend typecheck/build + secret scan.
8. SES domain-identity runbook (and optional automation) so app VERIFIED matches AWS VERIFIED.
9. Idempotent billing checkout; never store provider secrets in examples.

### C. Medium-priority work

1. API key scopes, TEST vs LIVE send gate, `last_used_at`.
2. Enforce `EMAIL_READ` and method security consistently.
3. Webhook delivery-time SSRF re-check and delayed retries.
4. JDBC SSL; actuator metrics on a private port.
5. Dashboard real usage metrics instead of presentation charts.
6. Password reset, email verification, member invites.
7. `List-Unsubscribe` for bulk (less urgent if only transactional).
8. Dockerfiles / deploy unit files in-repo.
9. Re-verify domains on a schedule.

### D. Low-priority work

1. Campaigns, analytics/opens/clicks, attachments, visual template builder.
2. DKIM key rotation UI / dual selectors.
3. README rewrite; remove dead `OUTBOUND_QUEUE` if unused.
4. Publisher confirms; Jackson `ObjectMapper` bean cleanup.
5. Public Postfix MX / inbound DSN — **do not** schedule unless SES is abandoned.

### E. Already-complete functionality

- Modular monolith API with deny-all security default  
- BCrypt passwords, hashed dashboard sessions, hashed API keys  
- Tenant-scoped data access on the main resources  
- Plans, entitlements, usage quotas, send rate limit (Redis, fail-closed)  
- Template versions + safe `{{placeholders}}`  
- Domain DNS record generation and verify API  
- DKIM key generation, AES custody, rsa-sha256 signing  
- Async send: DB outbox → RabbitMQ → worker → MIME  
- SESv2 RAW send with throttling classification and public-delivery guard  
- SES SNS/SQS feedback consumer with bounce/complaint pipelines and idempotent hashes  
- Suppression on hard bounce and complaint  
- Signed customer webhooks with URL SSRF allowlist (create-time)  
- Razorpay webhook signature verification and event de-dupe  
- Flyway migrations + Hibernate validate  
- ECS structured logs, request id header, generic 500s  
- Actuator health (db/rabbit/redis/MTA) with details hidden in prod  
- Local Docker Compose for Postgres, RabbitMQ, Redis, Mailpit, locked-down Postfix  
- Substantial backend unit/IT coverage around delivery, DKIM, bounce, complaint, SES feedback  

---

## SES / bounce / DKIM verdict

No evidence in this audit that the **SES RAW + app DKIM + SQS bounce/complaint** design is defective. Gmail delivery success is consistent with that design. Recommendations above are isolation, abuse, secrets, observability, admin, and config-hardening — not a replacement MTA or a public bounce MX.
