package com.closecircle.api.sharing;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SharingService {
    private static final RowMapper<SharingSettings> SETTINGS = (rs, row) -> new SharingSettings(
            rs.getObject("group_id", UUID.class), rs.getObject("user_id", UUID.class),
            rs.getBoolean("sharing_enabled"), rs.getBoolean("share_live_location"),
            rs.getObject("updated_at", OffsetDateTime.class).toInstant());

    private final JdbcTemplate jdbc;

    public SharingService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public SharingSettings get(UUID groupId, UUID userId) {
        Boolean member = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM group_members WHERE group_id = ? AND user_id = ?)", Boolean.class, groupId, userId);
        if (!Boolean.TRUE.equals(member)) {
            throw notFound();
        }
        List<SharingSettings> saved = jdbc.query("""
                SELECT group_id, user_id, sharing_enabled, share_live_location, updated_at
                FROM shares WHERE group_id = ? AND user_id = ?
                """, SETTINGS, groupId, userId);
        return saved.isEmpty() ? SharingSettings.notShared(groupId, userId) : saved.getFirst();
    }

    @Transactional
    public SharingSettings update(UUID groupId, UUID userId, SharingUpdateRequest request) {
        List<UUID> membership = jdbc.query("""
                SELECT user_id FROM group_members WHERE group_id = ? AND user_id = ? FOR UPDATE
                """, (rs, row) -> rs.getObject("user_id", UUID.class), groupId, userId);
        if (membership.isEmpty()) {
            throw notFound();
        }

        // clock_timestamp() is the moment of the write, after the membership lock was acquired, not the
        // transaction start. Recordings made while this request waited for the lock therefore stay before
        // the consent boundary and are rejected by uploads.
        return jdbc.queryForObject("""
                INSERT INTO shares (group_id, user_id, sharing_enabled, share_live_location, updated_at)
                VALUES (?, ?, ?, ?, clock_timestamp())
                ON CONFLICT (group_id, user_id) DO UPDATE
                SET sharing_enabled = EXCLUDED.sharing_enabled,
                    share_live_location = EXCLUDED.share_live_location,
                    updated_at = CASE
                        WHEN shares.sharing_enabled <> EXCLUDED.sharing_enabled
                          OR shares.share_live_location <> EXCLUDED.share_live_location
                        THEN EXCLUDED.updated_at ELSE shares.updated_at END
                RETURNING group_id, user_id, sharing_enabled, share_live_location, updated_at
                """, SETTINGS, groupId, userId, request.sharingEnabled(), request.shareLiveLocation());
    }

    @Transactional
    public SharingSettings revoke(UUID groupId, UUID userId) {
        return update(groupId, userId, new SharingUpdateRequest(false, false));
    }

    private static ResponseStatusException notFound() {
        return SharingUpdateRequest.failure(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Group not found");
    }
}
