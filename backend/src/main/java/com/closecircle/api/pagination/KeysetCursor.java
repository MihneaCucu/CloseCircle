package com.closecircle.api.pagination;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.UUID;

import com.closecircle.api.error.ApiProblem;
import org.springframework.http.HttpStatus;

/**
 * Position of the last item on a page, sorted by (createdAt, id).
 * The next page continues strictly after it ("keyset pagination"). Unlike OFFSET, this doesn't
 * skip or repeat rows when items are added between requests.
 * Clients receive it as an opaque Base64 string and must not build it themselves.
 */
public record KeysetCursor(Instant createdAt, UUID id) {

    public String encode() {
        String raw = createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns null for a missing cursor (first page); rejects anything we did not issue with a 400. */
    public static KeysetCursor decodeOrNull(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            if (separator < 0) {
                throw new InvalidCursorException();
            }
            return new KeysetCursor(Instant.parse(raw.substring(0, separator)),
                    UUID.fromString(raw.substring(separator + 1)));
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new InvalidCursorException();
        }
    }

    static class InvalidCursorException extends ApiProblem {
        InvalidCursorException() {
            super(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "Invalid cursor",
                    "The cursor is not valid. Pass back the nextCursor value unchanged.");
        }
    }
}
