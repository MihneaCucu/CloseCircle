package com.closecircle.api.location;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationUploadService {
    public record Result(LatestLocationService.Location location, boolean created) { }
    private record Sharing(boolean active, java.time.Instant updatedAt, java.time.Instant expiresAt) { }

    private static final RowMapper<LatestLocationService.Location> LOCATION = (rs, row) ->
            new LatestLocationService.Location(rs.getObject("location_id", UUID.class),
                    rs.getObject("group_id", UUID.class), rs.getObject("user_id", UUID.class),
                    rs.getDouble("latitude"), rs.getDouble("longitude"), rs.getObject("accuracy_m", Double.class),
                    rs.getObject("recorded_at", OffsetDateTime.class).toInstant(),
                    rs.getObject("received_at", OffsetDateTime.class).toInstant());

    private final JdbcTemplate jdbc;
    private final LocationUploadPolicy policy;

    public LocationUploadService(JdbcTemplate jdbc, LocationUploadPolicy policy) {
        this.jdbc = jdbc;
        this.policy = policy;
    }

    @Transactional
    public Result upload(UUID groupId, UUID userId, LocationUploadRequest request, UUID uploadKey) {
        // Serialize writes for this membership, including retries, and prevent
        // deletion or a settings update from overtaking the consent check.
        List<UUID> membership = jdbc.query("""
                SELECT user_id FROM group_members WHERE group_id = ? AND user_id = ? FOR UPDATE
                """, (rs, row) -> rs.getObject("user_id", UUID.class), groupId, userId);
        if (membership.isEmpty()) {
            throw LocationUploadRequest.failure(HttpStatus.NOT_FOUND, "GROUP_NOT_FOUND", "Group not found");
        }
        // Temporary sharing (SCRUM-17) counts as disabled from expires_at onwards.
        List<Sharing> shares = jdbc.query("""
                SELECT sharing_enabled AND share_live_location
                           AND (expires_at IS NULL OR expires_at > clock_timestamp()),
                       updated_at, expires_at
                FROM shares WHERE group_id = ? AND user_id = ? FOR UPDATE
                """, (rs, row) -> {
                    OffsetDateTime expiresAt = rs.getObject(3, OffsetDateTime.class);
                    return new Sharing(rs.getBoolean(1), rs.getObject(2, OffsetDateTime.class).toInstant(),
                            expiresAt == null ? null : expiresAt.toInstant());
                }, groupId, userId);
        if (shares.isEmpty() || !shares.getFirst().active()) {
            throw LocationUploadRequest.failure(HttpStatus.FORBIDDEN, "LOCATION_SHARING_DISABLED",
                    "Location sharing is not enabled");
        }
        var receivedAt = policy.receivedAt();
        policy.validate(request.recordedAt(), shares.getFirst().updatedAt(), receivedAt);
        // With a future clock tolerance, a recording could claim a time after sharing expires.
        var expiresAt = shares.getFirst().expiresAt();
        if (expiresAt != null && !request.recordedAt().isBefore(expiresAt)) {
            throw LocationUploadRequest.failure(HttpStatus.FORBIDDEN, "LOCATION_SHARING_DISABLED",
                    "Recording is after location sharing expires");
        }
        if (uploadKey != null) {
            List<LatestLocationService.Location> previous = jdbc.query("""
                    SELECT location_id, group_id, user_id, latitude, longitude, accuracy_m, recorded_at, received_at
                    FROM locations WHERE group_id = ? AND user_id = ? AND client_upload_id = ?
                    """, LOCATION, groupId, userId, uploadKey);
            if (!previous.isEmpty()) {
                var point = previous.getFirst();
                if (Double.compare(point.latitude(), request.latitude()) != 0
                        || Double.compare(point.longitude(), request.longitude()) != 0
                        || !Objects.equals(point.accuracyM(), request.accuracyM())
                        || !point.recordedAt().equals(request.recordedAt())) {
                    throw LocationUploadRequest.failure(HttpStatus.CONFLICT, "IDEMPOTENCY_CONFLICT",
                            "Upload key was already used for a different recording");
                }
                return new Result(point, false);
            }
        }
        var location = jdbc.queryForObject("""
                INSERT INTO locations(location_id, group_id, user_id, latitude, longitude, accuracy_m,
                                      recorded_at, received_at, client_upload_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING location_id, group_id, user_id, latitude, longitude, accuracy_m, recorded_at, received_at
                """, LOCATION, UUID.randomUUID(), groupId, userId, request.latitude(), request.longitude(),
                request.accuracyM(), request.recordedAt().atOffset(ZoneOffset.UTC),
                receivedAt.atOffset(ZoneOffset.UTC), uploadKey);
        return new Result(location, true);
    }
}
