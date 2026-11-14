ALTER TABLE locations ADD COLUMN client_upload_id UUID;
ALTER TABLE locations ADD CONSTRAINT locations_group_user_upload_uq
    UNIQUE (group_id, user_id, client_upload_id);
