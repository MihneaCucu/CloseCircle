-- SCRUM-12: a group can have at most one PENDING invitation per email address
-- (compared case-insensitively). Enforced by the database, so two simultaneous
-- invitations for the same person cannot both succeed.
CREATE UNIQUE INDEX invitations_one_pending_per_email
    ON invitations (group_id, lower(invitee_email))
    WHERE status = 'PENDING';
