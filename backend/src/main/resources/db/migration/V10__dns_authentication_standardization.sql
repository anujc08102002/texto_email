-- Phase 8A Step 3: ownership is a dedicated TXT type; functional records stay SPF/DKIM/DMARC only.

ALTER TABLE domain_verification_records
    DROP CONSTRAINT IF EXISTS ck_domain_verification_type;

ALTER TABLE domain_verification_records
    ADD CONSTRAINT ck_domain_verification_type
        CHECK (type IN ('OWNERSHIP', 'SPF', 'DKIM', 'DMARC'));

ALTER TABLE domain_verification_records
    ADD COLUMN IF NOT EXISTS last_error VARCHAR(64);

-- Remove ownership tokens previously concatenated into functional records.
UPDATE domain_verification_records
SET value = TRIM(BOTH FROM regexp_replace(value, ';?[[:space:]]*texto-verify-[0-9a-fA-F]+', '', 'g'))
WHERE type IN ('SPF', 'DKIM', 'DMARC')
  AND value ILIKE '%texto-verify-%';
