package com.closecircle.api.location;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocationFreshnessPolicy {
    public record Metadata(LocationFreshness.Status status, Instant evaluatedAt, long recentForSeconds) { }

    private final Long recentForSeconds;
    private final Clock clock;

    @Autowired
    public LocationFreshnessPolicy(
            @Value("${closecircle.location.recent-for-seconds:#{null}}") String recentForSeconds) {
        this(recentForSeconds == null ? null : Long.valueOf(recentForSeconds), Clock.systemUTC());
    }

    LocationFreshnessPolicy(Long recentForSeconds, Clock clock) {
        if (recentForSeconds != null && recentForSeconds < 0) {
            throw new IllegalArgumentException("Freshness window must not be negative");
        }
        this.recentForSeconds = recentForSeconds;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    Instant now() {
        return clock.instant();
    }

    Metadata assess(Instant recordedAt, Instant evaluatedAt) {
        if (recentForSeconds == null) return null;
        var assessment = LocationFreshness.assess(recordedAt, Duration.ofSeconds(recentForSeconds),
                Clock.fixed(evaluatedAt, java.time.ZoneOffset.UTC));
        return new Metadata(assessment.status(), evaluatedAt, recentForSeconds);
    }
}
