# Texto Email Platform

Modular-monolith foundation for a multi-tenant Email Service Provider (ESP) SaaS.

This repository currently establishes the platform skeleton: Java 21 / Spring Boot 4 backend, Next.js dashboard, Docker Compose dependencies, Flyway-owned schema, health checks, and security architecture. Product features such as SMTP delivery, DKIM verification, campaigns, and billing providers are intentionally not implemented yet.

## Architecture overview

The backend is a **single Spring Boot application** organized by module packages. It is not a set of microservices.

```
email-platform/
├── backend/            Spring Boot 4.0.8 modular monolith (Java 21, Maven)
├── frontend/           Next.js 16.3.3 App Router dashboard
├── infrastructure/     Docker Compose for local dependencies
└── README.md
```

Backend modules (packages inside one application):

| Module | Responsibility |
| --- | --- |
| `auth` | Dashboard and API-key authentication contracts |
| `tenant` | Tenant records and request-scoped tenant context |
| `billing` | Future customer billing |
| `plan` | Subscription plans loaded from the database |
| `entitlement` | Future usage limits |
| `domain` | Future sending-domain verification |
| `email` | Future transactional/bulk sending |
| `template` | Future templates |
| `queue` | RabbitMQ topology for outbound, bounce, and complaint traffic |
| `delivery` | SMTP MTA client (`mailpit` local mailbox; `postfix` controlled local MTA) |
| `suppression` | Future suppression lists |
| `bounce` | RFC 3464 DSN ingest, token correlation, bounce policy (state + suppression). Not public inbound. |
| `complaint` | Provider-neutral complaint/FBL ingest, token correlation, complaint suppression. Not public FBL. |
| `analytics` | Future delivery analytics |
| `common` | API envelope, errors, logging, security |

The frontend consumes `/api/v1` rather than embedding business data.

## Prerequisites

- Java 21, with `JAVA_HOME` pointing at a JDK 21 install (not an IDE bundled JRE)
- Maven Wrapper (`backend/mvnw` / `backend/mvnw.cmd`)
- Node.js 20+ (Node 24 is fine)
- Docker Desktop with Docker Compose
- Git

## Local setup

1. Copy environment defaults:

```bash
cp .env.example .env
cp frontend/.env.example frontend/.env.local
```

2. Start infrastructure with Docker Compose.
3. Run Flyway by starting the backend (migrations apply on startup).
4. Start the frontend.

Do not commit `.env` or other secret files.

## Docker startup

From the repository root:

```bash
docker compose --env-file .env -f infrastructure/docker-compose.yml up -d
```

Wait until Postgres, RabbitMQ, Redis, Mailpit, and Postfix are healthy:

```bash
docker compose --env-file .env -f infrastructure/docker-compose.yml ps
```

Compose publishes Postgres on host port **5433** by default so it does not collide with a local PostgreSQL install on 5432. Override with `POSTGRES_HOST_PORT` in `.env` if needed, and keep `DATABASE_URL` on the same host port.

## Backend startup

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

On Windows:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
```

The API listens on `http://localhost:8080`.

## Frontend startup

```bash
cd frontend
npm install
npm run dev
```

The dashboard listens on `http://localhost:3000`.

## Database migration

Flyway owns schema changes. Hibernate is set to `ddl-auto=validate` and must not create or update tables.

Migrations live in `backend/src/main/resources/db/migration` and run automatically when the backend starts against PostgreSQL.

## Test commands

Backend:

```bash
cd backend
./mvnw test
```

Windows:

```powershell
cd backend
.\mvnw.cmd test
```

Frontend:

```bash
cd frontend
npm run lint
npm run build
```

## Service URLs

| Service | URL | Notes |
| --- | --- | --- |
| Frontend | http://localhost:3000 | Next.js dashboard |
| Backend API | http://localhost:8080/api/v1 | Versioned HTTP API |
| Platform status | http://localhost:8080/api/v1/status | Public |
| Plans | http://localhost:8080/api/v1/plans | Public, database-backed |
| Health | http://localhost:8080/actuator/health | Application, PostgreSQL, RabbitMQ, Redis |
| OpenAPI / Swagger | http://localhost:8080/swagger-ui.html | Enabled on the `local` profile only |
| PostgreSQL | localhost:5433 | Bound to 127.0.0.1; container still listens on 5432 |
| RabbitMQ AMQP | localhost:5672 | Bound to 127.0.0.1 |
| RabbitMQ management | http://localhost:15672 | Bound to 127.0.0.1 |
| Redis | localhost:6379 | Bound to 127.0.0.1 |
| Mailpit SMTP | localhost:1025 | Local mailbox sink only |
| Mailpit UI | http://localhost:8025 | Bound to 127.0.0.1 |
| Postfix SMTP | localhost:2525 | Controlled local MTA (`texto.test` only; bound to 127.0.0.1) |

## Environment variables

| Variable | Used by | Local default |
| --- | --- | --- |
| `DATABASE_URL` | Backend | `jdbc:postgresql://localhost:5433/email_platform` |
| `DATABASE_USERNAME` | Backend | `email_platform` |
| `DATABASE_PASSWORD` | Backend | `email_platform` |
| `POSTGRES_DB` | Docker Postgres | `email_platform` |
| `POSTGRES_USER` | Docker Postgres | `email_platform` |
| `POSTGRES_PASSWORD` | Docker Postgres | `email_platform` |
| `POSTGRES_HOST_PORT` | Docker Postgres | `5433` (host publish; container port stays 5432) |
| `RABBITMQ_HOST` | Backend | `localhost` |
| `RABBITMQ_PORT` | Backend | `5672` |
| `RABBITMQ_USERNAME` | Backend | `email_platform` |
| `RABBITMQ_PASSWORD` | Backend | `email_platform` |
| `RABBITMQ_DEFAULT_USER` | Docker RabbitMQ | `email_platform` |
| `RABBITMQ_DEFAULT_PASS` | Docker RabbitMQ | `email_platform` |
| `REDIS_HOST` | Backend | `localhost` |
| `REDIS_PORT` | Backend | `6379` |
| `EMAIL_RATE_LIMIT_PER_MINUTE` | Backend | `120` (accepted messages per tenant per UTC minute) |
| `MTA_HOSTNAME` | Backend | empty locally; required public FQDN in `prod` |
| `MTA_OUTBOUND_IP` | Backend | empty locally; required public IPv4 in `prod` |
| `MAILPIT_HOST` | Backend | `localhost` |
| `MAILPIT_SMTP_PORT` | Backend | `1025` |
| `MTA_IMPLEMENTATION` | Backend | `mailpit` (`postfix` for controlled local Postfix) |
| `MTA_SMTP_HOST` | Backend | `localhost` |
| `MTA_SMTP_PORT` | Backend | `1025` (Mailpit) / `2525` (local Postfix) |
| `CORS_ALLOWED_ORIGINS` | Backend | `http://localhost:3000` |
| `NEXT_PUBLIC_API_BASE_URL` | Frontend | `http://localhost:8080` |
| `BILLING_PROVIDER` | Backend | `noop` (`razorpay` for TEST billing) |
| `BILLING_GRACE_PERIOD_DAYS` | Backend | `3` |
| `RAZORPAY_KEY_ID` | Backend | empty (TEST key id) |
| `RAZORPAY_KEY_SECRET` | Backend | empty (never commit) |
| `RAZORPAY_WEBHOOK_SECRET` | Backend | empty (never commit) |

Production configuration (`application-prod.yml`) reads credentials only from the environment. It does not contain passwords or API secrets.

Actuator exposes only `health`. Sensitive management endpoints are not enabled.

## Phase 5 — async email delivery pipeline

Transactional send acceptance is asynchronous:

1. `POST /api/v1/emails` (optional `Idempotency-Key`) authorizes, validates sender/template, splits suppressed vs deliverable recipients, enforces the tenant **message** send-rate limit (`email-platform.email.rate-limit-per-minute`), consumes **one email unit per deliverable recipient** (monthly quota), persists `QUEUED`, writes an outbox event `EMAIL_DELIVERY_REQUESTED`, and emits `email.queued`. See [docs/email-send-rate-limit.md](docs/email-send-rate-limit.md).
2. `OutboxPublisher` polls unpublished outbox rows and publishes JSON jobs `{messageId, tenantId, attempt}` to RabbitMQ (`email.delivery.exchange` / `email.delivery.queue`).
3. `EmailDeliveryWorker` transitions `QUEUED`/`DEFERRED` → `PROCESSING` → `SENDING`, records `delivery_attempts`, and calls `DeliveryEngine`. `SmtpDeliveryEngine` composes MIME, DKIM-signs from the persisted encrypted key, and submits via `MtaClient` (`mailpit` by default; `postfix` for controlled local Postfix). Public Internet delivery stays off unless the dual kill switch is enabled. See [docs/mta-transport.md](docs/mta-transport.md), [docs/dkim-key-custody.md](docs/dkim-key-custody.md), and [docs/production-smtp.md](docs/production-smtp.md).
4. Outcomes: `DELIVERED`, temporary `DEFERRED` with TTL retry queues (30s → 2h), or permanent `FAILED` / `BOUNCED` (550), with matching webhooks.

Config under `email-platform.email`: `max-recipients`, `max-attempts`, `outbox-poll-ms`, `rate-limit-per-minute` (tenant-scoped accepted messages per UTC minute; Redis; fail-closed if Redis is down).

## Phase 6 — subscription billing (Razorpay TEST)

Billing is provider-agnostic. The database remains the source of truth for plan, subscription status, period, entitlements, and usage. Razorpay is only the payment provider.

| Item | Detail |
| --- | --- |
| Abstraction | `BillingProvider` (`noop` default, `razorpay` when `BILLING_PROVIDER=razorpay`) |
| Mapping | `provider_plan_mappings` links internal plans → Razorpay `plan_*` IDs (activate after replacing placeholders) |
| Checkout | `POST /api/v1/billing/checkout` → Razorpay subscription id + public `keyId` |
| Webhook | `POST /api/v1/billing/webhooks/razorpay` (signature verified; idempotent `billing_events`) |
| Config API | `GET /api/v1/billing/config` (provider + configured + public key id only) |

Env (TEST mode only — never commit secrets):

```
BILLING_PROVIDER=noop
BILLING_GRACE_PERIOD_DAYS=3
RAZORPAY_KEY_ID=
RAZORPAY_KEY_SECRET=
RAZORPAY_WEBHOOK_SECRET=
```

To enable local TEST checkout:

1. Create Razorpay **test** plans in the Razorpay dashboard.
2. Update `provider_plan_mappings.provider_plan_id` for STARTER/BUSINESS and set `active = true`.
3. Set `BILLING_PROVIDER=razorpay` and the three Razorpay env vars.
4. Point the Razorpay webhook to your backend `/api/v1/billing/webhooks/razorpay`.
5. Upgrade from **Billing** or **Plans** in the dashboard.
