ALTER TABLE campaign_recipients
    ADD COLUMN email_message_id UUID REFERENCES email_messages(id);

CREATE UNIQUE INDEX uk_campaign_recipient_email_message
    ON campaign_recipients(email_message_id)
    WHERE email_message_id IS NOT NULL;

WITH ranked_active_schedules AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY campaign_id ORDER BY updated_at DESC, created_at DESC, id DESC) AS position
    FROM campaign_schedules
    WHERE status = 'ACTIVE'
)
UPDATE campaign_schedules schedule
SET status = 'INVALIDATED',
    invalidated_at = COALESCE(schedule.invalidated_at, NOW()),
    updated_at = NOW()
FROM ranked_active_schedules ranked
WHERE schedule.id = ranked.id
  AND ranked.position > 1;

CREATE UNIQUE INDEX uk_campaign_schedule_one_active
    ON campaign_schedules(campaign_id)
    WHERE status = 'ACTIVE';