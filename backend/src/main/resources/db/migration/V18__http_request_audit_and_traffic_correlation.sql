-- HTTP request audit (no bodies, no credentials) and traffic correlation IDs.

CREATE TABLE http_request_audit (
    id UUID PRIMARY KEY,
    request_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128),
    occurred_at TIMESTAMPTZ NOT NULL,
    tenant_id UUID,
    principal_id UUID,
    api_key_id UUID,
    http_method VARCHAR(16) NOT NULL,
    route VARCHAR(255) NOT NULL,
    status_code INTEGER NOT NULL,
    duration_ms INTEGER NOT NULL,
    request_size_bytes BIGINT,
    response_size_bytes BIGINT,
    client_ip VARCHAR(64),
    user_agent VARCHAR(512),
    referer VARCHAR(512),
    auth_outcome VARCHAR(32) NOT NULL,
    error_code VARCHAR(64),
    error_category VARCHAR(64),
    email_message_id UUID,
    provider_message_id VARCHAR(255),
    operation_type VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_http_request_audit_created_at ON http_request_audit (created_at DESC);
CREATE INDEX idx_http_request_audit_tenant_id ON http_request_audit (tenant_id);
CREATE INDEX idx_http_request_audit_request_id ON http_request_audit (request_id);
CREATE INDEX idx_http_request_audit_status_code ON http_request_audit (status_code);
CREATE INDEX idx_http_request_audit_route ON http_request_audit (route);

ALTER TABLE email_messages
    ADD COLUMN request_id VARCHAR(128),
    ADD COLUMN trace_id VARCHAR(128);
CREATE INDEX idx_email_messages_request_id ON email_messages (request_id);

ALTER TABLE outbox_events
    ADD COLUMN request_id VARCHAR(128),
    ADD COLUMN trace_id VARCHAR(128);
CREATE INDEX idx_outbox_events_request_id ON outbox_events (request_id);

ALTER TABLE delivery_attempts
    ADD COLUMN request_id VARCHAR(128),
    ADD COLUMN trace_id VARCHAR(128);
CREATE INDEX idx_delivery_attempts_request_id ON delivery_attempts (request_id);

ALTER TABLE bounce_events
    ADD COLUMN request_id VARCHAR(128),
    ADD COLUMN trace_id VARCHAR(128);
CREATE INDEX idx_bounce_events_request_id ON bounce_events (request_id);

ALTER TABLE complaint_events
    ADD COLUMN request_id VARCHAR(128),
    ADD COLUMN trace_id VARCHAR(128);
CREATE INDEX idx_complaint_events_request_id ON complaint_events (request_id);
