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
    // PostgreSQL's timestamptz range: 4713 BC to 294276 AD (same bounds as LatestLocationService).
    // Java's Instant reaches much further; a value outside this range made the query fail with a 500.
    private static final Instant MIN_TIME = Instant.parse("-4713-11-24T00:00:00Z");
    private static final Instant END_TIME = Instant.parse("+294277-01-01T00:00:00Z"); // exclusive

    /** Far longer than any cursor we issue; rejected before decoding. */
    private static final int MAX_LENGTH = 200;

    public String encode() {
        String raw = createdAt + "|" + id;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Returns null for a missing cursor (first page); rejects anything we did not issue with a 400. */
    public static KeysetCursor decodeOrNull(String encoded) {
        if (encoded == null) {
            return null;
        }
        if (encoded.length() > MAX_LENGTH) {
            throw new InvalidCursorException();
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = raw.indexOf('|');
            if (separator < 0) {
                throw new InvalidCursorException();
            }
            Instant createdAt = Instant.parse(raw.substring(0, separator));
            String rawId = raw.substring(separator + 1);
            UUID id = UUID.fromString(rawId);
            // Reject anything we could never have issued, so the query only receives valid values.
            if (createdAt.isBefore(MIN_TIME) || !createdAt.isBefore(END_TIME)
                    || createdAt.getNano() % 1000 != 0             // finer than PostgreSQL's microseconds
                    || !id.toString().equalsIgnoreCase(rawId)) {   // non-canonical, e.g. "0-0-0-0-1"
                throw new InvalidCursorException();
            }
            return new KeysetCursor(createdAt, id);
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
