-- Production hardening identified by the readiness audit.

ALTER TABLE api_keys
    ADD COLUMN scopes VARCHAR(255) NOT NULL DEFAULT 'email:send,email:read';

ALTER TABLE outbox_events
    ADD COLUMN next_attempt_at TIMESTAMPTZ;

CREATE INDEX idx_outbox_publishable
    ON outbox_events (next_attempt_at, created_at)
    WHERE published_at IS NULL;

-- A verified sending identity can have exactly one tenant owner. Pending claims may
-- coexist until DNS ownership is proven.
CREATE UNIQUE INDEX uk_domains_verified_domain_global
    ON domains (LOWER(domain))
    WHERE status = 'VERIFIED';

CREATE INDEX idx_api_keys_tenant_status_created
    ON api_keys (tenant_id, status, created_at DESC);

