package com.closecircle.api.sharing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** The groups that can currently see the caller's location (SCRUM-18). */
public record SharedGroups(List<Item> items) {
    /**
     * @param expiresAt        when temporary sharing stops, or {@code null} for no time limit
     * @param remainingSeconds whole seconds left (rounded up), computed by the server so clients can count
     *                         down without trusting the phone's clock; {@code null} for no time limit
     */
    public record Item(UUID groupId, String groupName, Instant expiresAt, Long remainingSeconds,
                       Instant updatedAt) { }
}
