-- SES feedback: persist provider identity on bounce_events.
-- Does not alter DSN token authorization. Historical bounce rows remain valid (new columns nullable).
-- Complaint unique key is widened so one SES feedbackId can record multiple recipients.

ALTER TABLE bounce_events
    ADD COLUMN IF NOT EXISTS provider VARCHAR(64);

ALTER TABLE bounce_events
    ADD COLUMN IF NOT EXISTS provider_event_id VARCHAR(128);

CREATE UNIQUE INDEX IF NOT EXISTS uk_bounce_events_provider_event_recipient
    ON bounce_events (provider, provider_event_id, final_recipient)
    WHERE provider IS NOT NULL
      AND provider_event_id IS NOT NULL
      AND final_recipient IS NOT NULL;

DROP INDEX IF EXISTS uk_complaint_events_provider_event;

CREATE UNIQUE INDEX IF NOT EXISTS uk_complaint_events_provider_event_recipient
    ON complaint_events (provider, provider_event_id, recipient)
    WHERE provider_event_id IS NOT NULL
      AND recipient IS NOT NULL;
