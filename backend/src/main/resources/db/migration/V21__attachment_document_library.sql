-- Private-object metadata only. Binary content belongs in object storage, never PostgreSQL.
CREATE TABLE attachment_documents (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    original_filename VARCHAR(512) NOT NULL,
    normalized_filename VARCHAR(512) NOT NULL,
    content_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    checksum_sha256 CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    scan_detail VARCHAR(512),
    uploaded_by UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uk_attachment_documents_tenant_key UNIQUE(tenant_id, object_key),
    CONSTRAINT ck_attachment_documents_size CHECK(size_bytes >= 0),
    CONSTRAINT ck_attachment_documents_status CHECK(status IN ('UPLOADING','SCANNING','AVAILABLE','QUARANTINED','REJECTED','DELETED'))
);
CREATE INDEX idx_attachment_documents_tenant_status ON attachment_documents(tenant_id,status,created_at DESC);
CREATE INDEX idx_attachment_documents_tenant_checksum ON attachment_documents(tenant_id,checksum_sha256) WHERE status = 'AVAILABLE';
