package com.closecircle.api.location;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LocationFreshnessTest {
    private static final Instant NOW = Instant.parse("2026-11-04T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Duration WINDOW = Duration.ofMinutes(5);

    @Test
    void classifiesRecentAndThresholdBoundaryAndRetainsRecordingTime() {
        Instant recorded = NOW.minus(WINDOW);
        assertEquals(new LocationFreshness.Assessment(LocationFreshness.Status.RECENT, recorded),
                LocationFreshness.assess(recorded, WINDOW, CLOCK));
        assertEquals(LocationFreshness.Status.RECENT,
                LocationFreshness.assess(NOW.minusSeconds(1), WINDOW, CLOCK).status());
    }

    @Test
    void classifiesOlderLocationAsLastKnown() {
        assertEquals(LocationFreshness.Status.LAST_KNOWN,
                LocationFreshness.assess(NOW.minus(WINDOW).minusNanos(1), WINDOW, CLOCK).status());
    }

    @Test
    void doesNotTreatMissingOrFutureLocationAsRecent() {
        assertEquals(new LocationFreshness.Assessment(LocationFreshness.Status.UNKNOWN, null),
                LocationFreshness.assess(null, WINDOW, CLOCK));
        Instant future = NOW.plusSeconds(1);
        assertEquals(new LocationFreshness.Assessment(LocationFreshness.Status.UNKNOWN, future),
                LocationFreshness.assess(future, WINDOW, CLOCK));
    }

    @Test
    void rejectsInvalidPolicyAndSupportsZeroWindow() {
        assertThrows(IllegalArgumentException.class,
                () -> LocationFreshness.assess(NOW, Duration.ofSeconds(-1), CLOCK));
        assertThrows(NullPointerException.class, () -> LocationFreshness.assess(NOW, null, CLOCK));
        assertThrows(NullPointerException.class, () -> LocationFreshness.assess(NOW, WINDOW, null));
        assertEquals(LocationFreshness.Status.RECENT,
                LocationFreshness.assess(NOW, Duration.ZERO, CLOCK).status());
        assertEquals(LocationFreshness.Status.LAST_KNOWN,
                LocationFreshness.assess(NOW.minusNanos(1), Duration.ZERO, CLOCK).status());
    }
}
