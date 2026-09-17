-- Phase 8C Step 3: recipient-level bounce tracking on outbound messages.
-- Does not enable public MX. Does not change suppression uniqueness.

ALTER TABLE email_messages
    ADD COLUMN IF NOT EXISTS bounced_recipients jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE email_messages
    ADD COLUMN IF NOT EXISTS soft_bounced_recipients jsonb NOT NULL DEFAULT '[]'::jsonb;
