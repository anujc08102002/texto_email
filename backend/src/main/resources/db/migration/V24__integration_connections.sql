CREATE TABLE integration_connections (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    provider VARCHAR(64) NOT NULL,
    display_name VARCHAR(160) NOT NULL,
    status VARCHAR(32) NOT NULL,
    credential_reference VARCHAR(512),
    configuration JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_synced_at TIMESTAMPTZ,
    last_error VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_integration_connections_tenant_provider_name UNIQUE(tenant_id,provider,display_name),
    CONSTRAINT ck_integration_connections_status CHECK(status IN ('PENDING','CONNECTED','PAUSED','ERROR','DISCONNECTED'))
);
CREATE INDEX idx_integration_connections_tenant_status ON integration_connections(tenant_id,status);
