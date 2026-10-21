CREATE TABLE campaign_schedules (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    scheduled_at TIMESTAMPTZ NOT NULL,
    timezone VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    invalidated_at TIMESTAMPTZ,
    CONSTRAINT ck_campaign_schedules_status CHECK(status IN ('ACTIVE','INVALIDATED','EXECUTED','CANCELLED'))
);
CREATE UNIQUE INDEX uk_campaign_schedules_active ON campaign_schedules(campaign_id) WHERE status='ACTIVE';
CREATE INDEX idx_campaign_schedules_tenant_time ON campaign_schedules(tenant_id,scheduled_at) WHERE status='ACTIVE';
