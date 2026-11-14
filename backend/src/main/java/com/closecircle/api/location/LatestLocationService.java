package com.closecircle.api.location;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LatestLocationService {
    private final JdbcTemplate jdbc;

    public LatestLocationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Location(UUID locationId, UUID groupId, UUID userId, double latitude, double longitude,
                           Double accuracyM, Instant recordedAt, Instant receivedAt) { }
    public record MemberLocation(UUID userId, String displayName, Location location) { }
    public record Page(List<MemberLocation> items, String nextCursor) { }
    private record Row(MemberLocation item, Instant joinedAt) { }
    private record Cursor(Instant joinedAt, UUID userId) { }

    @Transactional(readOnly = true)
    public Page latest(UUID groupId, UUID viewerId, int requestedLimit, String encodedCursor) {
        int limit = Math.max(1, Math.min(100, requestedLimit));
        Cursor cursor = decode(encodedCursor);
        Boolean member = jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM group_members WHERE group_id = ? AND user_id = ?)
                """, Boolean.class, groupId, viewerId);
        if (!Boolean.TRUE.equals(member)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found");
        }

        OffsetDateTime since = cursor == null ? null : cursor.joinedAt().atOffset(ZoneOffset.UTC);
        UUID afterUser = cursor == null ? null : cursor.userId();
        List<Row> rows = jdbc.query("""
                SELECT gm.user_id, gm.joined_at, u.display_name,
                       l.location_id, l.latitude, l.longitude, l.accuracy_m, l.recorded_at, l.received_at
                FROM group_members gm
                JOIN users u ON u.user_id = gm.user_id
                JOIN shares s ON s.group_id = gm.group_id AND s.user_id = gm.user_id
                LEFT JOIN LATERAL (
                    SELECT location_id, latitude, longitude, accuracy_m, recorded_at, received_at
                    FROM locations WHERE group_id = gm.group_id AND user_id = gm.user_id
                    ORDER BY recorded_at DESC, location_id DESC LIMIT 1
                ) l ON true
                WHERE gm.group_id = ? AND gm.user_id <> ?
                  AND s.sharing_enabled = true AND s.share_live_location = true
                  AND EXISTS (SELECT 1 FROM group_members viewer
                              WHERE viewer.group_id = gm.group_id AND viewer.user_id = ?)
                  AND (CAST(? AS timestamptz) IS NULL OR
                       (gm.joined_at, gm.user_id) < (CAST(? AS timestamptz), CAST(? AS uuid)))
                ORDER BY gm.joined_at DESC, gm.user_id DESC LIMIT ?
                """, (rs, n) -> {
                    UUID userId = rs.getObject("user_id", UUID.class);
                    UUID locationId = rs.getObject("location_id", UUID.class);
                    Location location = locationId == null ? null : new Location(locationId, groupId, userId,
                            rs.getDouble("latitude"), rs.getDouble("longitude"),
                            rs.getObject("accuracy_m", Double.class),
                            rs.getObject("recorded_at", OffsetDateTime.class).toInstant(),
                            rs.getObject("received_at", OffsetDateTime.class).toInstant());
                    return new Row(new MemberLocation(userId, rs.getString("display_name"), location),
                            rs.getObject("joined_at", OffsetDateTime.class).toInstant());
                }, groupId, viewerId, viewerId, since, since, afterUser, limit + 1);
        boolean more = rows.size() > limit;
        List<Row> page = more ? rows.subList(0, limit) : rows;
        String next = more ? encode(page.get(page.size() - 1)) : null;
        return new Page(page.stream().map(Row::item).toList(), next);
    }

    private static String encode(Row row) {
        String value = row.joinedAt() + "/" + row.item().userId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decode(String value) {
        if (value == null) return null;
        if (value.length() > 200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            String[] parts = decoded.split("/", -1);
            if (parts.length != 2) throw new IllegalArgumentException();
            return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
    }
}
