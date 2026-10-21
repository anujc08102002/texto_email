-- Tenant-owned marketing foundation. Campaign sending remains asynchronous through the existing outbox.

CREATE TABLE contact_lists (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(160) NOT NULL,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (tenant_id, name)
);

CREATE TABLE contacts (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    email VARCHAR(320) NOT NULL,
    first_name VARCHAR(120), last_name VARCHAR(120), company VARCHAR(200), phone VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', subscription_status VARCHAR(32) NOT NULL DEFAULT 'SUBSCRIBED',
    source VARCHAR(64), properties JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (tenant_id, email),
    CONSTRAINT ck_contacts_status CHECK (status IN ('ACTIVE','ARCHIVED')),
    CONSTRAINT ck_contacts_subscription CHECK (subscription_status IN ('SUBSCRIBED','UNSUBSCRIBED','SUPPRESSED'))
);
CREATE INDEX idx_contacts_tenant_created ON contacts(tenant_id, created_at DESC);
CREATE INDEX idx_contacts_tenant_email ON contacts(tenant_id, email);

CREATE TABLE contact_list_members (
    contact_list_id UUID NOT NULL REFERENCES contact_lists(id) ON DELETE CASCADE,
    contact_id UUID NOT NULL REFERENCES contacts(id) ON DELETE CASCADE,
    PRIMARY KEY(contact_list_id, contact_id)
);

CREATE TABLE segments (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES tenants(id), name VARCHAR(160) NOT NULL,
    definition JSONB NOT NULL, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(tenant_id, name)
);

CREATE TABLE campaigns (
    id UUID PRIMARY KEY, tenant_id UUID NOT NULL REFERENCES tenants(id),
    name VARCHAR(160) NOT NULL, status VARCHAR(32) NOT NULL,
    sender VARCHAR(320), reply_to VARCHAR(320), subject TEXT, preheader TEXT, html_body TEXT, text_body TEXT,
    template_id UUID REFERENCES templates(id), segment_id UUID REFERENCES segments(id), contact_list_id UUID REFERENCES contact_lists(id),
    scheduled_at TIMESTAMPTZ, timezone VARCHAR(64), created_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_campaign_status CHECK (status IN ('DRAFT','SCHEDULED','QUEUED','PROCESSING','SENDING','COMPLETED','PARTIALLY_COMPLETED','FAILED','STOPPED','CANCELLED'))
);
CREATE INDEX idx_campaigns_tenant_status_updated ON campaigns(tenant_id, status, updated_at DESC);

CREATE TABLE campaign_recipients (
    id UUID PRIMARY KEY, campaign_id UUID NOT NULL REFERENCES campaigns(id) ON DELETE CASCADE,
    tenant_id UUID NOT NULL REFERENCES tenants(id), contact_id UUID REFERENCES contacts(id), email VARCHAR(320) NOT NULL,
    status VARCHAR(32) NOT NULL, variables JSONB NOT NULL DEFAULT '{}'::jsonb, error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(campaign_id, email)
);
CREATE INDEX idx_campaign_recipients_campaign_status ON campaign_recipients(campaign_id, status);
