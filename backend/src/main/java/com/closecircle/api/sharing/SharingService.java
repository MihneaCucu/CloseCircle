package com.closecircle.api.sharing;

import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SharingService {
    private final ShareRepository shares;

    public SharingService(ShareRepository shares) {
        this.shares = shares;
    }

    @Transactional(readOnly = true)
    public SharingPreferences getPreferences(UUID groupId, UUID userId) {
        requireMembership(groupId, userId);
        return shares.findByGroupIdAndUserId(groupId, userId)
                .map(SharingPreferences::from)
                .orElseGet(() -> SharingPreferences.notShared(groupId, userId));
    }

    @Transactional
    public SharingPreferences updatePreferences(
            UUID groupId, UUID userId, boolean sharingEnabled, boolean shareLiveLocation) {
        requireMembership(groupId, userId);
        try {
            shares.upsert(groupId, userId, sharingEnabled, shareLiveLocation);
        } catch (DataIntegrityViolationException e) {
            throw new GroupNotFoundException();
        }
        return shares.findByGroupIdAndUserId(groupId, userId)
                .map(SharingPreferences::from)
                .orElseThrow();
    }

    @Transactional(readOnly = true)
    public boolean canViewLocation(UUID groupId, UUID viewerId, UUID memberId) {
        if (!shares.isGroupMember(groupId, viewerId)) {
            return false;
        }
        if (viewerId.equals(memberId)) {
            return true;
        }
        return shares.findByGroupIdAndUserId(groupId, memberId)
                .map(Share::isSharingEnabled)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Set<UUID> findSharingMemberIds(UUID groupId) {
        return Set.copyOf(shares.findSharingUserIds(groupId));
    }

    private void requireMembership(UUID groupId, UUID userId) {
        if (!shares.isGroupMember(groupId, userId)) {
            throw new GroupNotFoundException();
        }
    }
}
