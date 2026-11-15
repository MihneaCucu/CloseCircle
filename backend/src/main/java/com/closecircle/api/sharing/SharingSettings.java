package com.closecircle.api.sharing;

import java.time.Instant;
import java.util.UUID;

public record SharingSettings(UUID groupId, UUID userId, boolean sharingEnabled, boolean shareLiveLocation,
                              Instant expiresAt, Instant updatedAt) {
    static SharingSettings notShared(UUID groupId, UUID userId) {
        return new SharingSettings(groupId, userId, false, false, null, null);
    }
}
