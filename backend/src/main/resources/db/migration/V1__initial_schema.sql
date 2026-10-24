CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_email_nonempty CHECK (length(trim(email)) > 0),
    CONSTRAINT users_display_name_nonempty CHECK (length(trim(display_name)) > 0)
);
CREATE UNIQUE INDEX users_email_lower_uq ON users (lower(email));

CREATE TABLE groups (
    group_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    created_by UUID NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT groups_name_nonempty CHECK (length(trim(name)) > 0)
);
CREATE INDEX groups_created_by_idx ON groups (created_by);

CREATE TABLE group_members (
    group_id UUID NOT NULL REFERENCES groups(group_id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    member_role VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, user_id),
    CONSTRAINT group_members_role_ck CHECK (member_role IN ('OWNER', 'ADMIN', 'MEMBER'))
);
CREATE INDEX group_members_user_idx ON group_members (user_id, group_id);

CREATE TABLE invitations (
    invitation_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES groups(group_id) ON DELETE CASCADE,
    invited_by UUID NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    invitee_email VARCHAR(320) NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ,
    CONSTRAINT invitations_status_ck CHECK (status IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED')),
    CONSTRAINT invitations_expiry_ck CHECK (expires_at > created_at),
    CONSTRAINT invitations_acceptance_ck CHECK ((status = 'ACCEPTED') = (accepted_at IS NOT NULL))
);
CREATE INDEX invitations_group_status_idx ON invitations (group_id, status);
CREATE INDEX invitations_email_lower_idx ON invitations (lower(invitee_email));

CREATE TABLE shares (
    share_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL,
    user_id UUID NOT NULL,
    sharing_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    share_live_location BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT shares_group_member_fk FOREIGN KEY (group_id, user_id)
        REFERENCES group_members(group_id, user_id) ON DELETE CASCADE,
    CONSTRAINT shares_group_user_uq UNIQUE (group_id, user_id)
);

CREATE TABLE locations (
    location_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL,
    user_id UUID NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy_m DOUBLE PRECISION,
    recorded_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT locations_group_member_fk FOREIGN KEY (group_id, user_id)
        REFERENCES group_members(group_id, user_id) ON DELETE CASCADE,
    CONSTRAINT locations_latitude_ck CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT locations_longitude_ck CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT locations_accuracy_ck CHECK (accuracy_m IS NULL OR accuracy_m >= 0)
);
CREATE INDEX locations_group_member_recorded_idx ON locations (group_id, user_id, recorded_at DESC);

CREATE TABLE zones (
    zone_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL REFERENCES groups(group_id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES users(user_id) ON DELETE RESTRICT,
    name VARCHAR(100) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    radius_m INTEGER NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT zones_name_nonempty CHECK (length(trim(name)) > 0),
    CONSTRAINT zones_latitude_ck CHECK (latitude BETWEEN -90 AND 90),
    CONSTRAINT zones_longitude_ck CHECK (longitude BETWEEN -180 AND 180),
    CONSTRAINT zones_radius_ck CHECK (radius_m BETWEEN 10 AND 100000)
);
CREATE INDEX zones_group_enabled_idx ON zones (group_id, enabled);

CREATE TABLE notifications (
    notification_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID REFERENCES groups(group_id) ON DELETE SET NULL,
    recipient_user_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    notification_type VARCHAR(40) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    read_at TIMESTAMPTZ,
    CONSTRAINT notifications_type_nonempty CHECK (length(trim(notification_type)) > 0)
);
CREATE INDEX notifications_recipient_unread_idx ON notifications (recipient_user_id, created_at DESC)
    WHERE read_at IS NULL;
