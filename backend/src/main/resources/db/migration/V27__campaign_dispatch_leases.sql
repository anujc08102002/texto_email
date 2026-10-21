ALTER TABLE campaigns ADD COLUMN dispatch_lease_id UUID;
ALTER TABLE campaigns ADD COLUMN dispatch_lease_until TIMESTAMPTZ;
CREATE INDEX idx_campaigns_dispatchable ON campaigns(status, dispatch_lease_until, scheduled_at);
-- Recover campaigns left processing by a previous application process.
UPDATE campaigns SET status = 'QUEUED' WHERE status = 'PROCESSING';
