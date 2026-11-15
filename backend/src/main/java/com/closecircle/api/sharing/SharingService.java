package com.closecircle.api.sharing;

import java.time.Instant;
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
    // Expired temporary sharing is reported as off. The same rule is applied by the location read and upload
    // queries, so sharing stops exactly at expires_at without a background job.
    private static final String COLUMNS = """
            group_id, user_id,
            sharing_enabled AND (expires_at IS NULL OR expires_at > clock_timestamp()) AS sharing_enabled,
            share_live_location, expires_at, updated_at
            """;

    private static final RowMapper<SharingSettings> SETTINGS = (rs, row) -> new SharingSettings(
            rs.getObject("group_id", UUID.class), rs.getObject("user_id", UUID.class),
            rs.getBoolean("sharing_enabled"), rs.getBoolean("share_live_location"),
            instant(rs.getObject("expires_at", OffsetDateTime.class)),
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
        List<SharingSettings> saved = jdbc.query("SELECT " + COLUMNS + " FROM shares WHERE group_id = ? AND user_id = ?",
                SETTINGS, groupId, userId);
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
        // the consent boundary and are rejected by uploads. It is read once, so expires_at is exactly
        // updated_at plus the duration, computed with the server's clock rather than the phone's.
        // updated_at also moves when the expiry changes: re-enabling after an expiry with the same flags
        // must not accept recordings made while sharing had stopped.
        return jdbc.queryForObject("""
                WITH t AS (SELECT clock_timestamp() AS at)
                INSERT INTO shares (group_id, user_id, sharing_enabled, share_live_location, expires_at, updated_at)
                SELECT CAST(? AS uuid), CAST(? AS uuid), CAST(? AS boolean), CAST(? AS boolean),
                       t.at + make_interval(mins => CAST(? AS integer)), t.at
                FROM t
                ON CONFLICT (group_id, user_id) DO UPDATE
                SET sharing_enabled = EXCLUDED.sharing_enabled,
                    share_live_location = EXCLUDED.share_live_location,
                    expires_at = EXCLUDED.expires_at,
                    updated_at = CASE
                        WHEN shares.sharing_enabled <> EXCLUDED.sharing_enabled
                          OR shares.share_live_location <> EXCLUDED.share_live_location
                          OR shares.expires_at IS DISTINCT FROM EXCLUDED.expires_at
                        THEN EXCLUDED.updated_at ELSE shares.updated_at END
                RETURNING """ + COLUMNS,
                SETTINGS, groupId, userId, request.sharingEnabled(), request.shareLiveLocation(),
                request.durationMinutes());
    }

    @Transactional
    public SharingSettings revoke(UUID groupId, UUID userId) {
        return update(groupId, userId, new SharingUpdateRequest(false, false, null));
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static ResponseStatusException notFound() {
        return SharingUpdateRequest.failure(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Group not found");
    }
}
