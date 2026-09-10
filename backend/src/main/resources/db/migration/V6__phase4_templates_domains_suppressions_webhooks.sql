-- Phase 4: templates (versioned), domains + DNS verification, suppressions expansion, webhooks, richer email messages.

-- ---------------------------------------------------------------------------
-- Templates (replace flat email_templates with versioned model)
-- ---------------------------------------------------------------------------
ALTER TABLE email_messages DROP CONSTRAINT IF EXISTS email_messages_template_id_fkey;

CREATE TABLE templates (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    name VARCHAR(255) NOT NULL,
    slug VARCHAR(128) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL,
    current_version_id UUID,
    created_by UUID REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_templates_tenant_slug UNIQUE (tenant_id, slug),
    CONSTRAINT ck_templates_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_templates_tenant_id ON templates (tenant_id);
CREATE INDEX idx_templates_tenant_status ON templates (tenant_id, status);

CREATE TABLE template_versions (
    id UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES templates (id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    subject VARCHAR(998) NOT NULL,
    html_content TEXT NOT NULL,
    text_content TEXT,
    variables_schema JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_template_versions_template_version UNIQUE (template_id, version),
    CONSTRAINT ck_template_versions_version_positive CHECK (version >= 1)
);

CREATE INDEX idx_template_versions_template_id ON template_versions (template_id);

ALTER TABLE templates
    ADD CONSTRAINT fk_templates_current_version
        FOREIGN KEY (current_version_id) REFERENCES template_versions (id);

-- Migrate any legacy flat templates into versioned model (dev DBs may be empty).
INSERT INTO templates (id, tenant_id, name, slug, description, status, current_version_id, created_by, created_at, updated_at)
SELECT
    et.id,
    et.tenant_id,
    et.name,
    lower(regexp_replace(regexp_replace(et.name, '[^a-zA-Z0-9]+', '-', 'g'), '(^-|-$)', '', 'g')) || '-' || substr(replace(et.id::text, '-', ''), 1, 8),
    NULL,
    'ACTIVE',
    NULL,
    NULL,
    et.created_at,
    et.updated_at
FROM email_templates et;

INSERT INTO template_versions (id, template_id, version, subject, html_content, text_content, variables_schema, created_by, created_at)
SELECT
    gen_random_uuid(),
    et.id,
    1,
    et.subject,
    et.html_body,
    NULL,
    '{}'::jsonb,
    NULL,
    et.created_at
FROM email_templates et;

UPDATE templates t
SET current_version_id = tv.id
FROM template_versions tv
WHERE tv.template_id = t.id AND tv.version = 1 AND t.current_version_id IS NULL;

DROP TABLE email_templates;

ALTER TABLE email_messages
    ADD CONSTRAINT fk_email_messages_template
        FOREIGN KEY (template_id) REFERENCES templates (id);

-- ---------------------------------------------------------------------------
-- Domains
-- ---------------------------------------------------------------------------
ALTER TABLE sending_domains RENAME TO domains;
ALTER TABLE domains RENAME COLUMN domain_name TO domain;
ALTER TABLE domains RENAME CONSTRAINT uk_sending_domains_tenant_name TO uk_domains_tenant_domain;

ALTER TABLE domains
    ADD COLUMN IF NOT EXISTS status VARCHAR(32);

UPDATE domains
SET status = CASE
    WHEN upper(verification_status) = 'VERIFIED' THEN 'VERIFIED'
    WHEN upper(verification_status) = 'FAILED' THEN 'FAILED'
    ELSE 'PENDING'
END
WHERE status IS NULL;

ALTER TABLE domains
    ALTER COLUMN status SET NOT NULL;

ALTER TABLE domains
    DROP CONSTRAINT IF EXISTS ck_domains_status;

ALTER TABLE domains
    ADD CONSTRAINT ck_domains_status CHECK (status IN ('PENDING', 'VERIFYING', 'VERIFIED', 'FAILED', 'DISABLED'));

ALTER TABLE domains
    DROP CONSTRAINT IF EXISTS ck_domains_verification_status;

ALTER TABLE domains
    ADD CONSTRAINT ck_domains_verification_status
        CHECK (verification_status IN ('PENDING', 'VERIFYING', 'VERIFIED', 'FAILED', 'DISABLED'));

CREATE INDEX IF NOT EXISTS idx_domains_tenant_id ON domains (tenant_id);
CREATE INDEX IF NOT EXISTS idx_domains_tenant_status ON domains (tenant_id, status);

CREATE TABLE domain_verification_records (
    id UUID PRIMARY KEY,
    domain_id UUID NOT NULL REFERENCES domains (id) ON DELETE CASCADE,
    type VARCHAR(16) NOT NULL,
    name VARCHAR(255) NOT NULL,
    value TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    selector VARCHAR(64),
    public_key TEXT,
    private_key_ref VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL,
    verified_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    CONSTRAINT ck_domain_verification_type CHECK (type IN ('SPF', 'DKIM', 'DMARC')),
    CONSTRAINT ck_domain_verification_status CHECK (status IN ('PENDING', 'VERIFIED', 'FAILED', 'MISSING'))
);

CREATE INDEX idx_domain_verification_domain_id ON domain_verification_records (domain_id);
CREATE UNIQUE INDEX uk_domain_verification_domain_type ON domain_verification_records (domain_id, type);

-- ---------------------------------------------------------------------------
-- Suppressions
-- ---------------------------------------------------------------------------
ALTER TABLE suppressions
    ADD COLUMN IF NOT EXISTS type VARCHAR(32),
    ADD COLUMN IF NOT EXISTS source VARCHAR(32),
    ADD COLUMN IF NOT EXISTS message_id UUID,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS normalized_email VARCHAR(320);

UPDATE suppressions
SET type = COALESCE(type, 'MANUAL'),
    source = COALESCE(source, 'USER'),
    normalized_email = COALESCE(normalized_email, lower(trim(email))),
    updated_at = COALESCE(updated_at, created_at);

ALTER TABLE suppressions
    ALTER COLUMN type SET NOT NULL,
    ALTER COLUMN source SET NOT NULL,
    ALTER COLUMN normalized_email SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE suppressions
    DROP CONSTRAINT IF EXISTS uk_suppressions_tenant_email;

ALTER TABLE suppressions
    ADD CONSTRAINT uk_suppressions_tenant_normalized_email UNIQUE (tenant_id, normalized_email);

ALTER TABLE suppressions
    DROP CONSTRAINT IF EXISTS ck_suppressions_type;

ALTER TABLE suppressions
    ADD CONSTRAINT ck_suppressions_type CHECK (type IN ('BOUNCE', 'COMPLAINT', 'UNSUBSCRIBE', 'MANUAL'));

ALTER TABLE suppressions
    DROP CONSTRAINT IF EXISTS ck_suppressions_source;

ALTER TABLE suppressions
    ADD CONSTRAINT ck_suppressions_source CHECK (source IN ('SYSTEM', 'USER', 'IMPORT', 'WEBHOOK', 'CAMPAIGN', 'API'));

CREATE INDEX IF NOT EXISTS idx_suppressions_tenant_id ON suppressions (tenant_id);
CREATE INDEX IF NOT EXISTS idx_suppressions_tenant_type ON suppressions (tenant_id, type);
CREATE INDEX IF NOT EXISTS idx_suppressions_tenant_normalized ON suppressions (tenant_id, normalized_email);

ALTER TABLE suppressions
    ADD CONSTRAINT fk_suppressions_message
        FOREIGN KEY (message_id) REFERENCES email_messages (id);

-- ---------------------------------------------------------------------------
-- Email messages enrichment
-- ---------------------------------------------------------------------------
ALTER TABLE email_messages
    ADD COLUMN IF NOT EXISTS from_address VARCHAR(320),
    ADD COLUMN IF NOT EXISTS html_body TEXT,
    ADD COLUMN IF NOT EXISTS text_body TEXT,
    ADD COLUMN IF NOT EXISTS template_version_id UUID,
    ADD COLUMN IF NOT EXISTS status_reason VARCHAR(128),
    ADD COLUMN IF NOT EXISTS provider_response TEXT;

ALTER TABLE email_messages
    ADD CONSTRAINT fk_email_messages_template_version
        FOREIGN KEY (template_version_id) REFERENCES template_versions (id);

CREATE INDEX IF NOT EXISTS idx_email_messages_tenant_created ON email_messages (tenant_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- Webhooks
-- ---------------------------------------------------------------------------
CREATE TABLE webhook_configs (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    url TEXT NOT NULL,
    description VARCHAR(512),
    status VARCHAR(32) NOT NULL,
    secret_prefix VARCHAR(16) NOT NULL,
    secret_hash VARCHAR(255) NOT NULL,
    event_types JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_webhook_configs_status CHECK (status IN ('ACTIVE', 'PAUSED', 'DISABLED'))
);

CREATE INDEX idx_webhook_configs_tenant_id ON webhook_configs (tenant_id);
CREATE INDEX idx_webhook_configs_tenant_status ON webhook_configs (tenant_id, status);

CREATE TABLE webhook_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    webhook_config_id UUID NOT NULL REFERENCES webhook_configs (id) ON DELETE CASCADE,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ,
    last_attempt_at TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    last_response_code INTEGER,
    last_error TEXT,
    source_message_id UUID REFERENCES email_messages (id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_webhook_events_status CHECK (status IN ('PENDING', 'DELIVERING', 'DELIVERED', 'RETRYING', 'FAILED')),
    CONSTRAINT uk_webhook_events_dedupe UNIQUE (webhook_config_id, event_type, source_message_id)
);

CREATE INDEX idx_webhook_events_tenant_id ON webhook_events (tenant_id);
CREATE INDEX idx_webhook_events_config_id ON webhook_events (webhook_config_id);
CREATE INDEX idx_webhook_events_status_next ON webhook_events (status, next_attempt_at);
CREATE INDEX idx_webhook_events_created ON webhook_events (tenant_id, created_at DESC);
