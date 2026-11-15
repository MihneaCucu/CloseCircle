package com.closecircle.api.location;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record LocationUploadRequest(double latitude, double longitude, Double accuracyM, Instant recordedAt) {
    public LocationUploadRequest {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90
                || !Double.isFinite(longitude) || longitude < -180 || longitude > 180
                || (accuracyM != null && (!Double.isFinite(accuracyM) || accuracyM < 0))
                || recordedAt == null) {
            throw invalid();
        }
        recordedAt = recordedAt.truncatedTo(ChronoUnit.MICROS);
    }

    static LocationUploadRequest parse(JsonNode body) {
        if (body == null || !body.isObject()) throw invalid();
        var allowed = Set.of("latitude", "longitude", "accuracyM", "recordedAt");
        body.fieldNames().forEachRemaining(name -> {
            if (!allowed.contains(name)) throw invalid();
        });
        JsonNode time = body.get("recordedAt");
        if (time == null || !time.isTextual()) throw invalid();
        Instant recorded;
        try {
            recorded = OffsetDateTime.parse(time.textValue()).toInstant();
        } catch (DateTimeParseException ex) {
            throw invalid();
        }
        JsonNode accuracy = body.get("accuracyM");
        Double accuracyM = accuracy == null || accuracy.isNull() ? null : number(accuracy);
        return new LocationUploadRequest(number(body.get("latitude")), number(body.get("longitude")),
                accuracyM, recorded);
    }

    private static double number(JsonNode value) {
        if (value == null || !value.isNumber()) throw invalid();
        return value.doubleValue();
    }

    static ResponseStatusException invalid() {
        return failure(HttpStatus.BAD_REQUEST, "INVALID_LOCATION", "Invalid location request");
    }

    static ResponseStatusException failure(HttpStatus status, String code, String detail) {
        var failure = new ResponseStatusException(status, detail);
        failure.getBody().setProperty("code", code);
        return failure;
    }
}
