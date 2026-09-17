-- Phase 8D Step 1: dedicated DKIM private-key custody.
-- Does not enable public Internet delivery.
-- Does not drop domain_verification_records.private_key_ref (may still hold legacy pkcs8 material).

CREATE TABLE dkim_keys (
    id UUID PRIMARY KEY,
    domain_id UUID NOT NULL REFERENCES domains (id) ON DELETE CASCADE,
    selector VARCHAR(64) NOT NULL,
    algorithm VARCHAR(32) NOT NULL,
    key_size INTEGER NOT NULL,
    public_key TEXT NOT NULL,
    encrypted_private_key TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_dkim_keys_domain_selector UNIQUE (domain_id, selector),
    CONSTRAINT ck_dkim_keys_status CHECK (status IN ('ACTIVE', 'RETIRED', 'REVOKED')),
    CONSTRAINT ck_dkim_keys_algorithm CHECK (algorithm IN ('RSA')),
    CONSTRAINT ck_dkim_keys_key_size CHECK (key_size >= 2048)
);

CREATE UNIQUE INDEX uk_dkim_keys_one_active_per_domain
    ON dkim_keys (domain_id)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_dkim_keys_domain_id ON dkim_keys (domain_id);
