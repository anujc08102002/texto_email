-- Phase 8A Step 1: secure DKIM private-key custody.
-- Stores the DKIM signing key material for a domain. The public key mirrors what is published
-- in the DKIM DNS record (domain_verification_records); the private key is stored ONLY as
-- authenticated-encryption ciphertext (never plaintext) and is never searchable.
--
-- Rotation-ready: a domain may hold multiple selectors over time, but at most one ACTIVE key.
-- Tenant isolation is provided transitively via domain_id -> domains.tenant_id, matching the
-- existing domain_verification_records convention (no duplicated tenant_id column).
CREATE TABLE dkim_keys (
    id                    uuid        PRIMARY KEY,
    domain_id             uuid        NOT NULL REFERENCES domains (id) ON DELETE CASCADE,
    selector              varchar(63) NOT NULL,
    algorithm             varchar(16) NOT NULL,
    key_size              integer     NOT NULL,
    public_key            text        NOT NULL,
    encrypted_private_key text        NOT NULL,
    status                varchar(16) NOT NULL DEFAULT 'ACTIVE',
    created_at            timestamptz NOT NULL,
    updated_at            timestamptz NOT NULL,
    CONSTRAINT ck_dkim_keys_status CHECK (status IN ('ACTIVE', 'RETIRED', 'REVOKED')),
    CONSTRAINT uk_dkim_keys_domain_selector UNIQUE (domain_id, selector)
);

CREATE INDEX idx_dkim_keys_domain_id ON dkim_keys (domain_id);

-- At most one active signing key per domain (rotation flips the active key without deleting old ones).
CREATE UNIQUE INDEX uk_dkim_keys_domain_active ON dkim_keys (domain_id) WHERE status = 'ACTIVE';
