package com.closecircle.api.sharing;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShareRepository extends JpaRepository<Share, UUID> {
    Optional<Share> findByGroupIdAndUserId(UUID groupId, UUID userId);

    @Query("select s.userId from Share s where s.groupId = :groupId and s.sharingEnabled = true")
    List<UUID> findSharingUserIds(@Param("groupId") UUID groupId);

    @Query(value = """
            SELECT EXISTS (
                SELECT 1 FROM group_members WHERE group_id = :groupId AND user_id = :userId
            )
            """, nativeQuery = true)
    boolean isGroupMember(@Param("groupId") UUID groupId, @Param("userId") UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO shares (group_id, user_id, sharing_enabled, share_live_location, updated_at)
            VALUES (:groupId, :userId, :sharingEnabled, :shareLiveLocation, now())
            ON CONFLICT (group_id, user_id) DO UPDATE
            SET sharing_enabled = EXCLUDED.sharing_enabled,
                share_live_location = EXCLUDED.share_live_location,
                updated_at = EXCLUDED.updated_at
            """, nativeQuery = true)
    void upsert(@Param("groupId") UUID groupId, @Param("userId") UUID userId,
                @Param("sharingEnabled") boolean sharingEnabled, @Param("shareLiveLocation") boolean shareLiveLocation);
}
