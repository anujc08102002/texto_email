CREATE TABLE campaign_unsubscribe_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    email VARCHAR(320) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_campaign_unsubscribe_tenant ON campaign_unsubscribe_tokens(tenant_id);
CREATE INDEX idx_campaign_dispatch_pending ON campaign_recipients(campaign_id, tenant_id, created_at) WHERE status = 'PENDING';
