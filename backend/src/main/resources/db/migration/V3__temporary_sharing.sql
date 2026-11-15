-- SCRUM-17: temporary sharing. NULL means sharing has no time limit. Sharing is active only while
-- expires_at is NULL or in the future; readers apply this rule, so expiry needs no background job.
ALTER TABLE shares ADD COLUMN expires_at TIMESTAMPTZ;
