# Textonext → Texto Email feature-parity matrix

## Audit scope

Reference (read-only): `D:\TEXTO_Projects\Textonext` at `b8906bae`.

Target: `D:\TEXTO_Projects\Texto_email` at `86d29fbe` plus pre-existing, uncommitted local changes. This matrix describes the audited baseline; it does not treat a UI mock-up as an implemented feature.

The target already provides a Spring Boot 4 / Java 21 backend with PostgreSQL/Flyway, RabbitMQ, Redis, SES v2, tenant-aware authentication, domain/DKIM verification, transactional delivery, suppression, bounce/complaint processing, templates, billing, usage, API keys, webhooks, observability, and an admin/ops console. Its Next.js frontend already has dashboard, email, templates, domains, suppression, billing, API-key, webhook, analytics, and admin routes.

| Textonext feature | Email equivalent | Status | Frontend | Backend | Tests |
|---|---|---|---|---|---|
| Tenant dashboard | Email delivery and usage dashboard | IN_PROGRESS | Existing dashboard and analytics screens | Existing analytics, usage, and domain APIs | Partial existing coverage |
| Sidebar, header, responsive dashboard shell | Email tenant shell using the Textonext information architecture | IN_PROGRESS | Full-width 70px header and light emerald 288px/72px collapsible rail now follow the reference geometry; target workspace switcher, search, theme, and notifications remain | Not applicable | Frontend build and lint pass; visual parity review remains |
| Shared data grid | Search, filters, sorting, paging, column visibility | NOT_STARTED | Basic table primitives exist; reusable data grid absent | List contracts need pagination/filtering | Not present |
| Templates | Email templates, versions, HTML/plain text preview | IN_PROGRESS | Template list, create/edit/detail screens; schema-driven merge-field insertion; sandboxed preview | Versioned template module and existing renderer | Frontend typecheck and production build pass; module tests exist |
| Contacts / profiles | Email contacts and contact lists | IN_PROGRESS | Tenant-scoped create/delete/search with server-side pagination and page-size selection; custom properties, bulk selection, sorting, and column visibility remain | Tenant-scoped CRUD, search, paging, duplicate protection; JSONB properties persist as typed objects | Frontend build/lint pass; campaign/report integration covers contact create |
| Contact import | CSV/XLS/XLSX mapping and validation | IN_PROGRESS | CSV upload, field mapping, 10-row preview, local validation, duplicate detection within file, and rejected-row download; XLS/XLSX and custom-property mapping pending | Tenant-safe CSV import with 5 MB/10,000-row limits, email validation, and authoritative duplicate checks; parser still reads line-by-line | Frontend build/lint pass; backend import tests pending |
| Custom properties | Tenant-owned contact fields | NOT_STARTED | No UI | No model/API | Not present |
| Segments | Dynamic contact segments with preview | IN_PROGRESS | Visual rule editor with nested AND/OR groups, text/number/date operators, custom-property keys, and pre-save contact preview | Tenant-bound, parameterized preview endpoint; only active subscribed contacts are included, with paged results | 5 focused backend tests pass; frontend build/lint pass; saved-rule edit and preview pagination remain |
| Single campaign | Recipient-selected, scheduled email campaign | IN_PROGRESS | Explicit draft composer, active-template/inline content, paged contact selection, send confirmation, schedule/reschedule, stop, resume, searchable status-filtered campaign table, detail view, and paged recipient rows | Tenant-safe recipient expansion; creator permission rechecked; sends through EmailService, quota, suppression, idempotency, and outbox | Lifecycle, dispatch, personalizer tests pass; report flow integration passes; recipient preview pending |
| Bulk campaign | Chunked, idempotent bulk email processing | IN_PROGRESS | Composer supports paged contact selection and multiple recipient additions; spreadsheet audience import pending | Bounded 50-recipient dispatch batches, stable per-recipient idempotency, 429/503 retry, email outbox integration | Dispatcher/lifecycle unit tests pass; end-to-end send worker tests pending |
| Campaign drafts | Explicit draft save only | IN_PROGRESS | Draft persists only on explicit Save Draft; unsaved composer state is local | Creator user ID and tenant ownership are stored; recipients can only be added while DRAFT | Lifecycle tests pass; exact navigate-away regression test pending |
| Campaign scheduling | Authoritative server scheduling, cancel/reschedule | IN_PROGRESS | Schedule/reschedule controls and stop/resume actions | Due-campaign scheduler, atomic claim, one-active-schedule index, reschedule invalidation | Lifecycle tests pass; V25 migration/context test passes |
| Campaign controls | Stop, retry, resend failed, clone | IN_PROGRESS | Stop/resume controls are present; resend/clone UI pending | Stop invalidates schedules; retryable transport errors retain PENDING recipients; resend-failed/clone API pending | Lifecycle and dispatcher tests pass; stop-during-batch tests pending |
| Personalization | Merge fields and recipient preview | IN_PROGRESS | Template schema is shown in composer; recipient-specific rendered preview pending | Inline personalization escapes HTML values; current template version renders through existing renderer and declared variables | Personalizer unit tests pass; recipient preview tests pending |
| Test send | Non-analytic test rendering/delivery | NOT_STARTED | No campaign-specific test-send action yet | Existing `/emails/test` route exists but is not integrated with campaign recipient/template preview | Not present |
| Attachment library | Private tenant-scoped document library | IN_PROGRESS | Upload UI intentionally deferred until storage enforcement exists | Private attachment metadata schema, lifecycle states, checksum indexing, and replaceable object-storage contract | Backend compile verified; storage adapter/upload tests pending |
| Attachment validation | MIME, extension, size, checksum, scan state | NOT_STARTED | No UI | No upload/scan pipeline | Not present |
| Attachment rules | Spreadsheet/contact-to-document matching | IN_PROGRESS | Rule-builder UI deferred until documents can be safely uploaded and scanned | Tenant-scoped rule/condition schema, failure policies, deterministic comparisons, and safe filename-template rendering | Backend compile verified; resolver integration tests pending |
| Conditional attachments | Rule groups and safe failure policy | NOT_STARTED | No UI | No model/engine | Not present |
| Campaign reporting | Recipient-level delivery, attachment, and error reports | IN_PROGRESS | Detail screen shows aggregate metrics and paged recipient status/errors; export and rendered-email/attachment inspection pending | Paged recipient endpoint; counts derive from email, bounce, and complaint state; MTA acceptance is separate and not counted as recipient delivery | PostgreSQL integration verifies queued, accepted, bounce, complaint, and no false delivery count |
| Email delivery analytics | Sent, delivered, bounced, complained, suppression data | IN_PROGRESS | Analytics views exist | Analytics, bounce, complaint, suppression modules exist | Existing module tests |
| Sending domains and identities | Domain verification, DKIM/SPF/DMARC | IMPLEMENTED | Domain views exist | Domain and DNS/DKIM modules exist | Existing module tests |
| Suppressions | Tenant suppression and unsubscribe checks | IMPLEMENTED | Suppression manager exists | Suppression module exists | Existing module tests |
| Bounce and complaint processing | SES feedback and recipient state | IMPLEMENTED | Admin/ops views exist | Bounce, complaint, SES feedback modules exist | Existing module tests |
| Subscription / wallet | Plans, entitlements, usage, billing | IN_PROGRESS | Plans and billing routes exist | Billing, subscription, usage, entitlement modules exist | Existing module tests |
| Developer API and webhooks | Email-native API and event webhooks | IN_PROGRESS | API-key and webhook routes exist | API-key and webhook modules exist | Existing module tests |
| CRM / commerce integrations | Email contact, order, campaign integrations | IN_PROGRESS | Provider screens deferred until credentials can be connected safely | Tenant-scoped connection schema and credential-store boundary; supports Zapier, Zoho, Shopify, SAP, and Google Sheets provider identities | Backend compile verified; provider adapters pending |
| Email automation / flow builder | Event-triggered email workflows | NOT_STARTED | No UI | No workflow model/worker | Not present |
| Admin console alignment | Textonext-style operations console without secrets | IN_PROGRESS | Existing responsive operations shell regrouped into Main, Email delivery, Functionality, Configuration, and Governance & audit; only functional admin routes exposed | Ops APIs/modules exist | Frontend typecheck and production build pass |

## Required implementation sequence

1. Preserve and stabilize the existing target worktree; establish API contracts and database migrations for contacts, campaigns, recipients, schedules, imports, and attachments.
2. Port the Textonext layout and reusable table/dialog/filter interaction patterns into the target design system.
3. Build contacts, custom properties, imports, and segments before campaign composition, because campaigns require a tenant-safe recipient source.
4. Build campaign state transitions, recipient expansion, idempotent outbox/queue processing, suppression checks, and analytics before exposing bulk-send controls.
5. Add object storage and the document/attachment pipeline before attachment rules or spreadsheet matching.
6. Implement campaign UI only alongside functional APIs and tests; do not introduce non-functional navigation or controls.

## Non-negotiable preservation constraints

- Keep the Spring Boot, PostgreSQL/Flyway, RabbitMQ, Redis, SES v2, outbox, tenant-isolation, DKIM/domain, suppression, billing, entitlement, API-key, and webhook systems.
- Do not modify the Textonext reference repository.
- Resolve tenant ownership on the server, never from a frontend-supplied tenant identifier.
- Do not persist a campaign solely because a user enters a name; persist only on explicit draft-save or send/schedule intent.
- Store attachments outside PostgreSQL with private object keys, tenant authorization, validation, checksum, audit records, and a malware-scan hook.

## Security hardening delivered

- Contact custom properties are parsed and canonicalized as JSON objects server-side; malformed JSON is rejected with a client error.
- Attachment validation is allow-list based, rejects executable and script extensions, enforces a filename and MIME-type bound, and caps an individual attachment at 10 MB before it can enter storage or delivery processing.

## 19 October 2026 update

See [production readiness](production-readiness.md) for the current implementation and test evidence. Since the audited baseline above, segment JSONB persistence and empty contact search were fixed using a real PostgreSQL instance; saved segment editing/preview pagination, whole-audience campaign expansion, draft reopening/editing, recipient removal, personalized preview/test send, template-version pinning, clone, streaming CSV export, unsubscribe handling, dispatch leases and short transaction boundaries were added. The tenant shell now copies the reference logo/header/profile/sidebar styling directly. Contacts, campaigns, and segments use the ported Textonext DataGrid, with server sorting/pagination for large contact and campaign datasets, local segment column filters, and saved column visibility. Full pixel parity and the remaining attachment/integration/automation scope are not claimed.
