package com.closecircle.api.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class KeysetCursorTest {

    @Test
    void encodedCursorDecodesToTheSamePosition() {
        var cursor = new KeysetCursor(Instant.parse("2026-11-15T10:16:00.123456Z"), UUID.randomUUID());

        assertThat(KeysetCursor.decodeOrNull(cursor.encode())).isEqualTo(cursor);
    }

    @Test
    void missingCursorMeansFirstPage() {
        assertThat(KeysetCursor.decodeOrNull(null)).isNull();
    }

    @Test
    void tamperedCursorsAreRejected() {
        for (String bad : new String[] {"garbage!", "bm8tc2VwYXJhdG9y", "bm90LWEtZGF0ZXxub3QtYS11dWlk", ""}) {
            assertThatThrownBy(() -> KeysetCursor.decodeOrNull(bad))
                    .as(bad)
                    .isInstanceOf(KeysetCursor.InvalidCursorException.class);
        }
    }

    @Test
    void timestampsAtPostgresLimitsAreAccepted() {
        for (String time : new String[] {"-4713-11-24T00:00:00Z", "+294276-12-31T23:59:59.999999Z"}) {
            var cursor = new KeysetCursor(Instant.parse(time), UUID.randomUUID());
            assertThat(KeysetCursor.decodeOrNull(cursor.encode())).as(time).isEqualTo(cursor);
        }
    }

    @Test
    void cursorsWeCouldNeverHaveIssuedAreRejected() {
        String id = "00000000-0000-0000-0000-000000000001";
        String[] raw = {
                "+294277-01-01T00:00:00Z|" + id,         // after PostgreSQL's range (was HTTP 500)
                "+1000000000-01-01T00:00:00Z|" + id,     // far after it
                "-4713-11-23T23:59:59.999999Z|" + id,    // before PostgreSQL's range
                "2026-01-01T00:00:00.000000001Z|" + id,  // finer than microseconds
                "2026-01-01T00:00:00Z|0-0-0-0-1",        // non-canonical UUID
        };
        for (String value : raw) {
            assertThatThrownBy(() -> KeysetCursor.decodeOrNull(encode(value)))
                    .as(value)
                    .isInstanceOf(KeysetCursor.InvalidCursorException.class);
        }
        assertThatThrownBy(() -> KeysetCursor.decodeOrNull("A".repeat(201)))
                .isInstanceOf(KeysetCursor.InvalidCursorException.class);
    }

    private static String encode(String raw) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void limitIsDefaultedAndClamped() {
        assertThat(PageResponse.clampLimit(null)).isEqualTo(50);
        assertThat(PageResponse.clampLimit(0)).isEqualTo(1);
        assertThat(PageResponse.clampLimit(500)).isEqualTo(100);
        assertThat(PageResponse.clampLimit(20)).isEqualTo(20);
    }
}
