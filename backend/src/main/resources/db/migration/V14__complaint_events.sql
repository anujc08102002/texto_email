-- Phase 8C Step 4: complaint / feedback-loop event store.
-- Does not enable public FBL, Gmail/Yahoo/Outlook feeds, or public Internet delivery.
-- Complaints are not DSNs and do not reuse bounce_events.

CREATE TABLE complaint_events (
    id UUID PRIMARY KEY,
    tenant_id UUID REFERENCES tenants (id),
    email_message_id UUID REFERENCES email_messages (id),
    event_hash VARCHAR(64) NOT NULL,
    correlation_status VARCHAR(32) NOT NULL,
    processing_status VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    provider_event_id VARCHAR(128),
    complaint_type VARCHAR(32) NOT NULL,
    recipient VARCHAR(320),
    stored_recipient VARCHAR(320),
    original_message_id VARCHAR(255),
    provider_message_id VARCHAR(255),
    correlation_token_fp VARCHAR(8),
    occurred_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ NOT NULL,
    diagnostic VARCHAR(512),
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_complaint_events_event_hash UNIQUE (event_hash),
    CONSTRAINT ck_complaint_events_correlation CHECK (correlation_status IN (
        'MATCHED', 'UNMATCHED', 'PARSE_FAILED'
    )),
    CONSTRAINT ck_complaint_events_processing CHECK (processing_status IN (
        'APPLIED', 'NO_ACTION', 'UNCORRELATED', 'PARSE_FAILED'
    )),
    CONSTRAINT ck_complaint_events_type CHECK (complaint_type IN (
        'ABUSE', 'SPAM', 'FRAUD', 'UNKNOWN'
    ))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_complaint_events_provider_event
    ON complaint_events (provider, provider_event_id)
    WHERE provider_event_id IS NOT NULL;

CREATE INDEX idx_complaint_events_tenant_message
    ON complaint_events (tenant_id, email_message_id);

CREATE INDEX idx_complaint_events_tenant_received
    ON complaint_events (tenant_id, received_at DESC);
