ALTER TABLE campaigns ADD COLUMN attachment_ids JSONB NOT NULL DEFAULT '[]'::jsonb;
