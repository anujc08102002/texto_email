-- Phase 2 SaaS core: features, plan limits, subscriptions, usage, API key metadata

ALTER TABLE plans ADD COLUMN IF NOT EXISTS sort_order INTEGER NOT NULL DEFAULT 0;

ALTER TABLE api_keys
    ALTER COLUMN key_prefix TYPE VARCHAR(32);

ALTER TABLE api_keys
    ADD COLUMN IF NOT EXISTS environment VARCHAR(16) NOT NULL DEFAULT 'TEST',
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS revoked_at TIMESTAMPTZ;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS first_name VARCHAR(120),
    ADD COLUMN IF NOT EXISTS last_name VARCHAR(120);

CREATE TABLE features (
    id UUID PRIMARY KEY,
    code VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_features_code UNIQUE (code)
);

CREATE TABLE plan_features (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES plans (id),
    feature_id UUID NOT NULL REFERENCES features (id),
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uk_plan_features_plan_feature UNIQUE (plan_id, feature_id)
);

CREATE INDEX idx_plan_features_plan_id ON plan_features (plan_id);

CREATE TABLE plan_limits (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES plans (id),
    metric VARCHAR(128) NOT NULL,
    limit_value BIGINT,
    CONSTRAINT uk_plan_limits_plan_metric UNIQUE (plan_id, metric)
);

CREATE INDEX idx_plan_limits_plan_id ON plan_limits (plan_id);

CREATE TABLE subscriptions (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    plan_id UUID NOT NULL REFERENCES plans (id),
    status VARCHAR(32) NOT NULL,
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end TIMESTAMPTZ NOT NULL,
    trial_start TIMESTAMPTZ,
    trial_end TIMESTAMPTZ,
    cancel_at_period_end BOOLEAN NOT NULL DEFAULT FALSE,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_subscriptions_tenant_id ON subscriptions (tenant_id);
CREATE INDEX idx_subscriptions_tenant_status ON subscriptions (tenant_id, status);

CREATE TABLE tenant_usage (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    metric VARCHAR(128) NOT NULL,
    used BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_tenant_usage_period_metric UNIQUE (tenant_id, period_start, metric),
    CONSTRAINT ck_tenant_usage_used_nonneg CHECK (used >= 0)
);

CREATE INDEX idx_tenant_usage_tenant_id ON tenant_usage (tenant_id);

-- Retire legacy denormalized entitlements; Phase 2 uses plan_features + plan_limits
DROP TABLE IF EXISTS entitlements;

-- Deactivate legacy plan catalog
UPDATE plans SET active = FALSE, updated_at = TIMESTAMPTZ '2026-09-08 00:00:00+00'
WHERE code IN ('starter', 'growth', 'scale');

INSERT INTO plans (id, code, name, description, active, sort_order, created_at, updated_at)
VALUES
    ('a1000000-0000-4000-8000-000000000001', 'FREE', 'Free', 'Development starter workspace with limited sending.', TRUE, 10, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('a1000000-0000-4000-8000-000000000002', 'STARTER', 'Starter', 'For early products with moderate transactional volume.', TRUE, 20, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('a1000000-0000-4000-8000-000000000003', 'BUSINESS', 'Business', 'Higher volume with campaigns and advanced analytics.', TRUE, 30, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('a1000000-0000-4000-8000-000000000004', 'ENTERPRISE', 'Enterprise', 'Configurable enterprise capacity and controls.', TRUE, 40, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00');

INSERT INTO features (id, code, name, description, active, created_at, updated_at)
VALUES
    ('b1000000-0000-4000-8000-000000000001', 'API_SENDING', 'API sending', 'Send email through the HTTP API', TRUE, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('b1000000-0000-4000-8000-000000000002', 'SMTP_SENDING', 'SMTP sending', 'Send email through SMTP', TRUE, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('b1000000-0000-4000-8000-000000000003', 'CUSTOM_DOMAIN', 'Custom domain', 'Verify and send from custom domains', TRUE, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('b1000000-0000-4000-8000-000000000004', 'TEMPLATES', 'Templates', 'Reusable email templates', TRUE, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('b1000000-0000-4000-8000-000000000005', 'WEBHOOKS', 'Webhooks', 'Event webhooks', TRUE, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('b1000000-0000-4000-8000-000000000006', 'BASIC_ANALYTICS', 'Basic analytics', 'Core delivery analytics', TRUE, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('b1000000-0000-4000-8000-000000000007', 'ADVANCED_ANALYTICS', 'Advanced analytics', 'Advanced delivery analytics', TRUE, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('b1000000-0000-4000-8000-000000000008', 'SUPPRESSION', 'Suppressions', 'Suppression list management', TRUE, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('b1000000-0000-4000-8000-000000000009', 'BOUNCE_HANDLING', 'Bounce handling', 'Automatic bounce processing', TRUE, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('b1000000-0000-4000-8000-00000000000a', 'CAMPAIGNS', 'Campaigns', 'Marketing campaigns', TRUE, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('b1000000-0000-4000-8000-00000000000b', 'SEGMENTATION', 'Segmentation', 'Audience segmentation', TRUE, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('b1000000-0000-4000-8000-00000000000c', 'CUSTOM_TRACKING_DOMAIN', 'Custom tracking domain', 'Branded tracking domains', TRUE, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('b1000000-0000-4000-8000-00000000000d', 'DEDICATED_IP', 'Dedicated IP', 'Dedicated sending IP', TRUE, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('b1000000-0000-4000-8000-00000000000e', 'IP_POOLS', 'IP pools', 'IP pool management', TRUE, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00'),
    ('b1000000-0000-4000-8000-00000000000f', 'REPUTATION_DASHBOARD', 'Reputation dashboard', 'Sender reputation insights', TRUE, TIMESTAMPTZ '2026-09-08 00:00:00+00', TIMESTAMPTZ '2026-09-08 00:00:00+00');
