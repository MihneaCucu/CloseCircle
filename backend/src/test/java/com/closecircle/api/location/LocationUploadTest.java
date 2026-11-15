package com.closecircle.api.location;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class LocationUploadTest {
    private final ObjectMapper json = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-11-14T12:00:00Z");

    @Test
    void acceptsBoundaryCoordinatesAndNormalizesTimeToDatabasePrecision() throws Exception {
        var point = LocationUploadRequest.parse(json.readTree("""
                {"latitude":-90,"longitude":180,"recordedAt":"2026-11-14T14:00:00.123456789+02:00"}
                """));
        assertEquals(Instant.parse("2026-11-14T12:00:00.123456Z"), point.recordedAt());
        assertNull(point.accuracyM());
        assertEquals(-90, point.latitude());
        assertEquals(180, point.longitude());
    }

    @Test
    void rejectsNonFiniteCoordinatesAndAccuracy() {
        assertThrows(ResponseStatusException.class, () -> new LocationUploadRequest(Double.NaN, 0, null, NOW));
        assertThrows(ResponseStatusException.class, () -> new LocationUploadRequest(0, Double.POSITIVE_INFINITY, null, NOW));
        assertThrows(ResponseStatusException.class, () -> new LocationUploadRequest(0, 0, Double.NaN, NOW));
        assertThrows(ResponseStatusException.class, () -> new LocationUploadRequest(0, 0, -1.0, NOW));
    }

    @Test
    void rejectsIdentityOverridesAndMissingOffsets() throws Exception {
        assertThrows(ResponseStatusException.class, () -> LocationUploadRequest.parse(json.readTree("""
                {"latitude":0,"longitude":0,"recordedAt":"2026-11-14T12:00:00Z","userId":"other"}
                """)));
        assertThrows(ResponseStatusException.class, () -> LocationUploadRequest.parse(json.readTree("""
                {"latitude":0,"longitude":0,"recordedAt":"2026-11-14T12:00:00"}
                """)));
    }

    @Test
    void checksClockToleranceAndConsentBoundaries() {
        var policy = new LocationUploadPolicy(30, Clock.fixed(NOW, ZoneOffset.UTC));
        assertEquals(NOW, policy.receivedAt());
        assertDoesNotThrow(() -> policy.validate(NOW.plusSeconds(30), NOW, NOW));
        assertThrows(ResponseStatusException.class, () -> policy.validate(NOW.plusSeconds(30).plusNanos(1000), NOW, NOW));
        var revoked = assertThrows(ResponseStatusException.class, () -> policy.validate(NOW.minusNanos(1000), NOW, NOW));
        assertEquals(403, revoked.getStatusCode().value());
    }

    @Test
    void rejectsUnsafeClockConfiguration() {
        for (long value : new long[] {-1, 301, Long.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class,
                    () -> new LocationUploadPolicy(value, Clock.fixed(NOW, ZoneOffset.UTC)));
        }
    }
}
