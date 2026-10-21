CREATE TABLE attachment_rules (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    campaign_id UUID REFERENCES campaigns(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    matching_mode VARCHAR(32) NOT NULL,
    filename_template VARCHAR(512),
    failure_policy VARCHAR(32) NOT NULL,
    required BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_attachment_rules_mode CHECK(matching_mode IN ('EXACT','CASE_INSENSITIVE','NORMALIZED','CONTAINS','FILENAME_TEMPLATE','SAFE_REGEX')),
    CONSTRAINT ck_attachment_rules_policy CHECK(failure_policy IN ('SKIP_RECIPIENT','SEND_WITHOUT_ATTACHMENT','STOP_CAMPAIGN','MARK_RECIPIENT_FAILED'))
);
CREATE INDEX idx_attachment_rules_tenant_campaign ON attachment_rules(tenant_id,campaign_id);

CREATE TABLE attachment_rule_conditions (
    id UUID PRIMARY KEY,
    attachment_rule_id UUID NOT NULL REFERENCES attachment_rules(id) ON DELETE CASCADE,
    group_key VARCHAR(64) NOT NULL DEFAULT 'root',
    logical_operator VARCHAR(3) NOT NULL DEFAULT 'AND',
    source_field VARCHAR(160) NOT NULL,
    document_field VARCHAR(160) NOT NULL,
    comparison VARCHAR(32) NOT NULL,
    position INTEGER NOT NULL,
    CONSTRAINT ck_attachment_rule_conditions_operator CHECK(logical_operator IN ('AND','OR')),
    CONSTRAINT ck_attachment_rule_conditions_comparison CHECK(comparison IN ('EQUALS','CONTAINS','PREFIX','SUFFIX'))
);
CREATE INDEX idx_attachment_rule_conditions_rule ON attachment_rule_conditions(attachment_rule_id,position);
