package org.liteorm.test;

import org.junit.jupiter.api.Test;
import org.liteorm.runtime.ResultValueConverters;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;

class ResultValueConvertersTest {

    @Test
    void rejectsOffsetDateTimeConversionWithoutInstantOrOffset() {
        assertThrows(IllegalArgumentException.class, () ->
            ResultValueConverters.toOffsetDateTime(LocalDateTime.of(2026, 8, 31, 12, 34, 56)));
    }
}
