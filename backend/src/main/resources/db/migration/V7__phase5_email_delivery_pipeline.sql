-- Phase 5: async email delivery — message enrichment, idempotency, attempts, outbox, retry support.

-- ---------------------------------------------------------------------------
-- email_messages enrichment
-- ---------------------------------------------------------------------------
ALTER TABLE email_messages
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(128),
    ADD COLUMN IF NOT EXISTS reply_to VARCHAR(320),
    ADD COLUMN IF NOT EXISTS recipients_to JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS recipients_cc JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS recipients_bcc JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS suppressed_recipients JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN IF NOT EXISTS metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN IF NOT EXISTS provider_message_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS max_attempts INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS queued_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS processing_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS sending_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS delivered_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS failed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS last_error TEXT,
    ADD COLUMN IF NOT EXISTS created_by UUID REFERENCES users (id);

-- Backfill recipient arrays from legacy single recipient column
UPDATE email_messages
SET recipients_to = jsonb_build_array(recipient)
WHERE (recipients_to = '[]'::jsonb OR recipients_to IS NULL)
  AND recipient IS NOT NULL
  AND recipient <> '';

UPDATE email_messages
SET queued_at = COALESCE(queued_at, created_at)
WHERE status = 'QUEUED' OR status = 'DELIVERED' OR status = 'FAILED' OR status = 'SENDING'
   OR status = 'PROCESSING' OR status = 'DEFERRED' OR status = 'BOUNCED' OR status = 'SUPPRESSED';

CREATE UNIQUE INDEX IF NOT EXISTS uk_email_messages_tenant_idempotency
    ON email_messages (tenant_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_email_messages_tenant_status ON email_messages (tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_email_messages_tenant_created ON email_messages (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_email_messages_provider_message_id ON email_messages (provider_message_id)
    WHERE provider_message_id IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_email_messages_next_attempt ON email_messages (status, next_attempt_at)
    WHERE next_attempt_at IS NOT NULL;

-- ---------------------------------------------------------------------------
-- delivery_attempts
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS delivery_attempts (
    id UUID PRIMARY KEY,
    message_id UUID NOT NULL REFERENCES email_messages (id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    attempt_number INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    provider_response TEXT,
    error_category VARCHAR(64),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_delivery_attempts_message_attempt UNIQUE (message_id, attempt_number),
    CONSTRAINT ck_delivery_attempts_status CHECK (status IN (
        'STARTED', 'SUCCESS', 'TEMPORARY_FAILURE', 'PERMANENT_FAILURE'
    ))
);

CREATE INDEX IF NOT EXISTS idx_delivery_attempts_message_id ON delivery_attempts (message_id);
CREATE INDEX IF NOT EXISTS idx_delivery_attempts_tenant_id ON delivery_attempts (tenant_id);

-- ---------------------------------------------------------------------------
-- transactional outbox
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS outbox_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0,
    last_error TEXT
);

CREATE INDEX IF NOT EXISTS idx_outbox_unpublished
    ON outbox_events (created_at)
    WHERE published_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_outbox_tenant_id ON outbox_events (tenant_id);
CREATE INDEX IF NOT EXISTS idx_outbox_aggregate ON outbox_events (aggregate_type, aggregate_id);
