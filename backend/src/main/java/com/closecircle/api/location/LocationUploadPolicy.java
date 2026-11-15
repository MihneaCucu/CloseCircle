package com.closecircle.api.location;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LocationUploadPolicy {
    private final long maxFutureSeconds;
    private final Clock clock;

    @Autowired
    public LocationUploadPolicy(@Value("${closecircle.location.upload-max-future-seconds:0}") String seconds) {
        this(Long.parseLong(seconds), Clock.systemUTC());
    }

    LocationUploadPolicy(long maxFutureSeconds, Clock clock) {
        if (maxFutureSeconds < 0 || maxFutureSeconds > 300) {
            throw new IllegalArgumentException("Upload clock tolerance must be between 0 and 300 seconds");
        }
        this.maxFutureSeconds = maxFutureSeconds;
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    Instant receivedAt() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    void validate(Instant recordedAt, Instant consentUpdatedAt, Instant receivedAt) {
        if (recordedAt.isAfter(receivedAt.plusSeconds(maxFutureSeconds))) {
            throw LocationUploadRequest.invalid();
        }
        if (recordedAt.isBefore(consentUpdatedAt)) {
            throw LocationUploadRequest.failure(HttpStatus.FORBIDDEN, "LOCATION_CONSENT_CHANGED",
                    "Recording predates the current sharing preferences");
        }
    }
}
