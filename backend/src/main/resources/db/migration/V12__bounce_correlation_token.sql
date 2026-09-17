-- Phase 8C Step 2: opaque bounce correlation token + nullable tenant on unattributed DSN events.
-- Does not enable public MX or mutate delivery/suppression.

ALTER TABLE email_messages
    ADD COLUMN IF NOT EXISTS bounce_correlation_token VARCHAR(32);

CREATE UNIQUE INDEX IF NOT EXISTS uk_email_messages_bounce_correlation_token
    ON email_messages (bounce_correlation_token)
    WHERE bounce_correlation_token IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_email_messages_tenant_bounce_correlation_token
    ON email_messages (tenant_id, bounce_correlation_token)
    WHERE bounce_correlation_token IS NOT NULL;

ALTER TABLE bounce_events
    ALTER COLUMN tenant_id DROP NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_bounce_events_event_hash
    ON bounce_events (event_hash);
