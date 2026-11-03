package com.closecircle.api.sharing;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;


@Entity
@Table(name = "shares")
public class Share {
    @Id
    @Column(name = "share_id")
    private UUID shareId;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "sharing_enabled", nullable = false)
    private boolean sharingEnabled;

    @Column(name = "share_live_location", nullable = false)
    private boolean shareLiveLocation;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Share() {
    }

    public UUID getShareId() {
        return shareId;
    }

    public UUID getGroupId() {
        return groupId;
    }

    public UUID getUserId() {
        return userId;
    }

    public boolean isSharingEnabled() {
        return sharingEnabled;
    }

    public boolean isShareLiveLocation() {
        return shareLiveLocation;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
