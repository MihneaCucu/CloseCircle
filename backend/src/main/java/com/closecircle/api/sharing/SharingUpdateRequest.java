package com.closecircle.api.sharing;

import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * @param durationMinutes how long sharing stays on, or {@code null} for no time limit
 */
public record SharingUpdateRequest(boolean sharingEnabled, boolean shareLiveLocation, Integer durationMinutes) {
    static final int MAX_DURATION_MINUTES = 7 * 24 * 60;
    private static final Set<String> REQUIRED = Set.of("sharingEnabled", "shareLiveLocation");
    private static final Set<String> FIELDS = Set.of("sharingEnabled", "shareLiveLocation", "durationMinutes");

    public SharingUpdateRequest {
        if (durationMinutes != null
                && (!sharingEnabled || durationMinutes < 1 || durationMinutes > MAX_DURATION_MINUTES)) {
            throw invalid();
        }
    }

    static SharingUpdateRequest parse(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw invalid();
        }
        body.fieldNames().forEachRemaining(name -> {
            if (!FIELDS.contains(name)) {
                throw invalid();
            }
        });
        REQUIRED.forEach(name -> {
            if (!body.has(name)) {
                throw invalid();
            }
        });
        return new SharingUpdateRequest(flag(body.get("sharingEnabled")), flag(body.get("shareLiveLocation")),
                duration(body.get("durationMinutes")));
    }

    private static boolean flag(JsonNode value) {
        if (value == null || !value.isBoolean()) {
            throw invalid();
        }
        return value.booleanValue();
    }

    private static Integer duration(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        // Whole JSON numbers only: 1.5, "60" and true are rejected rather than coerced.
        if (!value.isIntegralNumber() || !value.canConvertToInt()) {
            throw invalid();
        }
        return value.intValue();
    }

    static ResponseStatusException invalid() {
        return failure(HttpStatus.BAD_REQUEST, "INVALID_SHARING_REQUEST", "Invalid sharing request");
    }

    static ResponseStatusException failure(HttpStatus status, String code, String detail) {
        var failure = new ResponseStatusException(status, detail);
        failure.getBody().setProperty("code", code);
        return failure;
    }
}
