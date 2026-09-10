-- Phase 6: subscription billing (provider-agnostic mappings + webhook audit)

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS provider VARCHAR(32),
    ADD COLUMN IF NOT EXISTS provider_customer_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS provider_subscription_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS provider_status VARCHAR(64),
    ADD COLUMN IF NOT EXISTS pending_plan_id UUID REFERENCES plans (id),
    ADD COLUMN IF NOT EXISTS grace_period_ends_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS notes TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uk_subscriptions_provider_subscription_id
    ON subscriptions (provider, provider_subscription_id)
    WHERE provider_subscription_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_subscriptions_provider_customer_id
    ON subscriptions (provider_customer_id)
    WHERE provider_customer_id IS NOT NULL;

ALTER TABLE billing_accounts
    ADD COLUMN IF NOT EXISTS provider VARCHAR(32),
    ADD COLUMN IF NOT EXISTS provider_customer_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS currency VARCHAR(8) NOT NULL DEFAULT 'INR',
    ADD COLUMN IF NOT EXISTS email VARCHAR(320);

CREATE UNIQUE INDEX IF NOT EXISTS uk_billing_accounts_provider_customer
    ON billing_accounts (provider, provider_customer_id)
    WHERE provider_customer_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS provider_plan_mappings (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES plans (id),
    provider VARCHAR(32) NOT NULL,
    provider_plan_id VARCHAR(128) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_provider_plan_mappings_plan_provider UNIQUE (plan_id, provider),
    CONSTRAINT uk_provider_plan_mappings_provider_plan UNIQUE (provider, provider_plan_id)
);

CREATE INDEX IF NOT EXISTS idx_provider_plan_mappings_provider
    ON provider_plan_mappings (provider, active);

CREATE TABLE IF NOT EXISTS billing_events (
    id UUID PRIMARY KEY,
    provider VARCHAR(32) NOT NULL,
    provider_event_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    tenant_id UUID REFERENCES tenants (id),
    subscription_id UUID REFERENCES subscriptions (id),
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    processing_status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    error_message TEXT,
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_billing_events_provider_event UNIQUE (provider, provider_event_id)
);

CREATE INDEX IF NOT EXISTS idx_billing_events_tenant_id ON billing_events (tenant_id);
CREATE INDEX IF NOT EXISTS idx_billing_events_created_at ON billing_events (created_at DESC);

-- Placeholder Razorpay plan IDs — replace with real test-mode plan_* IDs and set active=true.
-- FREE and ENTERPRISE are intentionally unmapped (self-serve checkout not applicable).
INSERT INTO provider_plan_mappings (id, plan_id, provider, provider_plan_id, active, created_at, updated_at)
VALUES
    (
        'b1000000-0000-4000-8000-000000000001',
        'a1000000-0000-4000-8000-000000000002',
        'RAZORPAY',
        'plan_REPLACE_STARTER',
        FALSE,
        TIMESTAMPTZ '2026-09-09 00:00:00+00',
        TIMESTAMPTZ '2026-09-09 00:00:00+00'
    ),
    (
        'b1000000-0000-4000-8000-000000000002',
        'a1000000-0000-4000-8000-000000000003',
        'RAZORPAY',
        'plan_REPLACE_BUSINESS',
        FALSE,
        TIMESTAMPTZ '2026-09-09 00:00:00+00',
        TIMESTAMPTZ '2026-09-09 00:00:00+00'
    )
ON CONFLICT DO NOTHING;
