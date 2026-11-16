package com.closecircle.api.invitation;

import java.time.Instant;
import java.util.UUID;

/**
 * An invitation as seen by the person invited: which group and who invited them.
 * The inviter's email is not included; the contract avoids exposing other users' emails.
 */
public record ReceivedInvitationResponse(UUID invitationId, UUID groupId, String groupName, Inviter invitedBy,
                                         InvitationStatus status, Instant createdAt, Instant expiresAt) {

    public record Inviter(UUID userId, String displayName) {
    }
}
