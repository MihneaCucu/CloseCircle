package com.closecircle.api.location;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Classifies a location using a caller-supplied policy and clock. */
public final class LocationFreshness {
    public enum Status { RECENT, LAST_KNOWN, UNKNOWN }

    /** The original recording time is retained for the client to display. */
    public record Assessment(Status status, Instant recordedAt) { }

    private LocationFreshness() { }

    public static Assessment assess(Instant recordedAt, Duration recentFor, Clock clock) {
        Objects.requireNonNull(recentFor, "recentFor");
        Objects.requireNonNull(clock, "clock");
        if (recentFor.isNegative()) {
            throw new IllegalArgumentException("recentFor must not be negative");
        }
        if (recordedAt == null) {
            return new Assessment(Status.UNKNOWN, null);
        }
        Instant now = clock.instant();
        if (recordedAt.isAfter(now)) {
            return new Assessment(Status.UNKNOWN, recordedAt);
        }
        Duration age = Duration.between(recordedAt, now);
        return new Assessment(age.compareTo(recentFor) <= 0 ? Status.RECENT : Status.LAST_KNOWN,
                recordedAt);
    }
}
