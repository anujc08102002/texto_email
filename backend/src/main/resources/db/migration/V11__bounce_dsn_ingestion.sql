-- Phase 8C Step 1: bounce/DSN ingestion foundation.
-- Does not enable public inbound MX or mutate delivery/suppression automatically.

ALTER TABLE email_messages
    ADD COLUMN IF NOT EXISTS rfc822_message_id VARCHAR(255);

CREATE UNIQUE INDEX IF NOT EXISTS uk_email_messages_tenant_rfc822_message_id
    ON email_messages (tenant_id, rfc822_message_id)
    WHERE rfc822_message_id IS NOT NULL;

CREATE TABLE bounce_events (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    email_message_id UUID REFERENCES email_messages (id),
    event_hash VARCHAR(64) NOT NULL,
    correlation_status VARCHAR(32) NOT NULL,
    bounce_class VARCHAR(32) NOT NULL,
    failure_kind VARCHAR(32) NOT NULL,
    dsn_action VARCHAR(32),
    status_code VARCHAR(32),
    diagnostic_code VARCHAR(64),
    diagnostic_message VARCHAR(512),
    original_recipient VARCHAR(320),
    final_recipient VARCHAR(320),
    original_sender VARCHAR(320),
    original_message_id VARCHAR(255),
    reporting_mta VARCHAR(255),
    remote_mta VARCHAR(255),
    arrival_date TIMESTAMPTZ,
    last_attempt_date TIMESTAMPTZ,
    will_retry_until TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_bounce_events_tenant_hash UNIQUE (tenant_id, event_hash),
    CONSTRAINT ck_bounce_events_correlation CHECK (correlation_status IN (
        'MATCHED', 'UNMATCHED', 'PARSE_FAILED'
    )),
    CONSTRAINT ck_bounce_events_class CHECK (bounce_class IN (
        'HARD_BOUNCE', 'SOFT_BOUNCE', 'UNKNOWN'
    )),
    CONSTRAINT ck_bounce_events_failure_kind CHECK (failure_kind IN (
        'PERMANENT_RECIPIENT',
        'TEMPORARY_RECIPIENT',
        'POLICY_REJECTION',
        'MAILBOX_UNAVAILABLE',
        'ADDRESS_RELATED',
        'UNKNOWN'
    ))
);

CREATE INDEX idx_bounce_events_tenant_message
    ON bounce_events (tenant_id, email_message_id);
CREATE INDEX idx_bounce_events_tenant_received
    ON bounce_events (tenant_id, received_at DESC);
