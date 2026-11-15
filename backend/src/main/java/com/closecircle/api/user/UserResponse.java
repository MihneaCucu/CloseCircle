package com.closecircle.api.user;

import java.time.Instant;
import java.util.UUID;

/** The contract's {@code user} object. The password hash is deliberately not part of it. */
public record UserResponse(UUID userId, String email, String displayName, Instant createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getCreatedAt());
    }
}
