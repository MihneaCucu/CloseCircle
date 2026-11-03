package com.closecircle.api.sharing;

import java.time.Instant;
import java.util.UUID;

public record SharingPreferences(
        UUID groupId,
        UUID userId,
        boolean sharingEnabled,
        boolean shareLiveLocation,
        Instant updatedAt) {

    static SharingPreferences from(Share share) {
        return new SharingPreferences(share.getGroupId(), share.getUserId(), share.isSharingEnabled(),
                                        share.isShareLiveLocation(), share.getUpdatedAt());
    }

    static SharingPreferences notShared(UUID groupId, UUID userId) {
        return new SharingPreferences(groupId, userId, false, false, null);
    }
}
