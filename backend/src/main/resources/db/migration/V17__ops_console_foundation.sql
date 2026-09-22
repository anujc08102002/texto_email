-- Operations console: platform-admin identity, audit log, and query indexes.
-- No seed passwords. Bootstrap is application-side and optional.

CREATE TABLE platform_admins (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_platform_admins_email UNIQUE (email),
    CONSTRAINT ck_platform_admins_status CHECK (status IN ('ACTIVE', 'DISABLED'))
);

CREATE UNIQUE INDEX uk_platform_admins_email_lower ON platform_admins (LOWER(email));

CREATE TABLE platform_admin_sessions (
    id UUID PRIMARY KEY,
    admin_id UUID NOT NULL REFERENCES platform_admins (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_platform_admin_sessions_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_platform_admin_sessions_admin_id ON platform_admin_sessions (admin_id);
CREATE INDEX idx_platform_admin_sessions_expires_at ON platform_admin_sessions (expires_at);

CREATE TABLE platform_audit_events (
    id UUID PRIMARY KEY,
    admin_id UUID REFERENCES platform_admins (id) ON DELETE SET NULL,
    method VARCHAR(16) NOT NULL,
    path VARCHAR(512) NOT NULL,
    query_string VARCHAR(1024),
    status_code INTEGER,
    duration_ms INTEGER,
    request_id VARCHAR(128),
    remote_address VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_platform_audit_events_created ON platform_audit_events (created_at DESC);
CREATE INDEX idx_platform_audit_events_admin_created ON platform_audit_events (admin_id, created_at DESC);

-- Cross-tenant ops list/filter indexes (tenant-scoped indexes already exist).
CREATE INDEX IF NOT EXISTS idx_email_messages_created ON email_messages (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_email_messages_status_created ON email_messages (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_email_messages_from_address ON email_messages (from_address);
CREATE INDEX IF NOT EXISTS idx_email_messages_recipient ON email_messages (recipient);

CREATE INDEX IF NOT EXISTS idx_delivery_attempts_created ON delivery_attempts (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_delivery_attempts_status_created ON delivery_attempts (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_delivery_attempts_error_category ON delivery_attempts (error_category)
    WHERE error_category IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_bounce_events_received ON bounce_events (received_at DESC);
CREATE INDEX IF NOT EXISTS idx_complaint_events_received ON complaint_events (received_at DESC);
CREATE INDEX IF NOT EXISTS idx_suppressions_created ON suppressions (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_webhook_events_created ON webhook_events (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_webhook_events_status_created ON webhook_events (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_outbox_created ON outbox_events (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_domains_created ON domains (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_created ON users (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_api_keys_created ON api_keys (created_at DESC);
CREATE INDEX IF NOT EXISTS idx_tenants_created ON tenants (created_at DESC);
