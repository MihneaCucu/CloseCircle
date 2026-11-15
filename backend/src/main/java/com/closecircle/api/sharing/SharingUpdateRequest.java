package com.closecircle.api.sharing;

import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record SharingUpdateRequest(boolean sharingEnabled, boolean shareLiveLocation) {
    private static final Set<String> FIELDS = Set.of("sharingEnabled", "shareLiveLocation");

    static SharingUpdateRequest parse(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != FIELDS.size()) {
            throw invalid();
        }
        body.fieldNames().forEachRemaining(name -> {
            if (!FIELDS.contains(name)) {
                throw invalid();
            }
        });
        return new SharingUpdateRequest(flag(body.get("sharingEnabled")), flag(body.get("shareLiveLocation")));
    }

    private static boolean flag(JsonNode value) {
        if (value == null || !value.isBoolean()) {
            throw invalid();
        }
        return value.booleanValue();
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
