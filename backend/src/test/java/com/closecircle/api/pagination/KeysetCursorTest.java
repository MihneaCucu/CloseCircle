package com.closecircle.api.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
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
    void limitIsDefaultedAndClamped() {
        assertThat(PageResponse.clampLimit(null)).isEqualTo(50);
        assertThat(PageResponse.clampLimit(0)).isEqualTo(1);
        assertThat(PageResponse.clampLimit(500)).isEqualTo(100);
        assertThat(PageResponse.clampLimit(20)).isEqualTo(20);
    }
}
