package com.closecircle.api.group;

import java.time.Instant;
import java.util.UUID;

/**
 * The contract's GroupSummary/GroupDetails shape (identical in v1).
 * {@code myRole} only informs the client's UI; the server never trusts it for authorization.
 */
public record GroupResponse(UUID groupId, String name, UUID createdBy, Instant createdAt,
                            MemberRole myRole, long memberCount) {
}
