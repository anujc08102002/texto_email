CREATE UNIQUE INDEX uk_users_email_lower ON users (LOWER(email));

CREATE TABLE dashboard_sessions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    tenant_id UUID NOT NULL REFERENCES tenants (id),
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_dashboard_sessions_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_dashboard_sessions_user_id ON dashboard_sessions (user_id);
