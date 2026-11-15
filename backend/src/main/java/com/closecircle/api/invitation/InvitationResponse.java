package com.closecircle.api.invitation;

import java.time.Instant;
import java.util.UUID;

/** The contract's {@code Invitation} object, as seen by the group's owner/admins. Never contains the token. */
public record InvitationResponse(UUID invitationId, UUID groupId, String email, InvitationStatus status,
                                 Instant createdAt, Instant expiresAt) {
}
