package com.closecircle.api.invitation;

/** Allowed values of {@code invitations.status} (database check constraint invitations_status_ck). */
public enum InvitationStatus {
    PENDING,
    ACCEPTED,
    REVOKED,
    EXPIRED
}
