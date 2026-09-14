package io.github.lynxus.test;

import org.junit.jupiter.api.Test;
import io.github.lynxus.runtime.ResultValueConverters;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.chrono.JapaneseDate;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResultValueConvertersTest {

    @Test
    void convertsStandardScalarAndCalendarJdbcValues() {
        LocalDate date = LocalDate.of(2026, 9, 6);
        LocalDateTime dateTime = LocalDateTime.of(2026, 9, 6, 7, 8, 9);
        Timestamp timestamp = Timestamp.valueOf(dateTime);

        assertAll(
            () -> assertEquals(new BigInteger("123"),
                ResultValueConverters.toBigInteger(new BigDecimal("123.75"))),
            () -> assertEquals(Year.of(2026), ResultValueConverters.toYear(2026L)),
            () -> assertEquals(Month.SEPTEMBER, ResultValueConverters.toMonth(9)),
            () -> assertEquals(YearMonth.of(2026, 9), ResultValueConverters.toYearMonth("2026-09")),
            () -> assertEquals(JapaneseDate.from(date),
                ResultValueConverters.toJapaneseDate(Date.valueOf(date))),
            () -> assertEquals(Date.valueOf(date), ResultValueConverters.toSqlDate(timestamp)),
            () -> assertEquals(Time.valueOf(LocalTime.of(7, 8, 9)),
                ResultValueConverters.toSqlTime(LocalTime.of(7, 8, 9))),
            () -> assertEquals(timestamp, ResultValueConverters.toSqlTimestamp(dateTime)),
            () -> assertEquals(timestamp.getTime(), ResultValueConverters.toUtilDate(timestamp).getTime())
        );
    }

    @Test
    void copiesBoxedByteArraysAndMutableUtilDates() {
        Byte[] boxedInput = {1, 2, 3};
        byte[] primitiveInput = {4, 5, 6};
        java.util.Date dateInput = new java.util.Date(1_789_000_000_000L);

        Byte[] boxedCopy = ResultValueConverters.toBoxedBytes(boxedInput);
        Byte[] primitiveCopy = ResultValueConverters.toBoxedBytes(primitiveInput);
        java.util.Date dateCopy = ResultValueConverters.toUtilDate(dateInput);

        assertArrayEquals(boxedInput, boxedCopy);
        assertNotSame(boxedInput, boxedCopy);
        assertArrayEquals(new Byte[]{4, 5, 6}, primitiveCopy);
        assertNotSame(dateInput, dateCopy);
        assertEquals(dateInput, dateCopy);
    }

    @Test
    void preservesNullsAndRejectsUnsupportedStandardConversions() {
        assertAll(
            () -> assertNull(ResultValueConverters.toBigInteger(null)),
            () -> assertNull(ResultValueConverters.toBoxedBytes(null)),
            () -> assertNull(ResultValueConverters.toUtilDate(null)),
            () -> assertNull(ResultValueConverters.toSqlDate(null)),
            () -> assertNull(ResultValueConverters.toSqlTime(null)),
            () -> assertNull(ResultValueConverters.toSqlTimestamp(null)),
            () -> assertNull(ResultValueConverters.toYear(null)),
            () -> assertNull(ResultValueConverters.toMonth(null)),
            () -> assertNull(ResultValueConverters.toYearMonth(null)),
            () -> assertNull(ResultValueConverters.toJapaneseDate(null)),
            () -> assertThrows(IllegalArgumentException.class,
                () -> ResultValueConverters.toBigInteger("123")),
            () -> assertThrows(IllegalArgumentException.class,
                () -> ResultValueConverters.toBoxedBytes("bytes")),
            () -> assertThrows(IllegalArgumentException.class,
                () -> ResultValueConverters.toUtilDate(dateTimeWithoutJdbcRepresentation())),
            () -> assertThrows(IllegalArgumentException.class,
                () -> ResultValueConverters.toSqlDate("2026-09-06")),
            () -> assertThrows(IllegalArgumentException.class,
                () -> ResultValueConverters.toSqlTime("07:08:09")),
            () -> assertThrows(IllegalArgumentException.class,
                () -> ResultValueConverters.toSqlTimestamp("2026-09-06T07:08:09")),
            () -> assertThrows(IllegalArgumentException.class,
                () -> ResultValueConverters.toYear("2026")),
            () -> assertThrows(IllegalArgumentException.class,
                () -> ResultValueConverters.toMonth("SEPTEMBER")),
            () -> assertThrows(IllegalArgumentException.class,
                () -> ResultValueConverters.toYearMonth(202609)),
            () -> assertThrows(IllegalArgumentException.class,
                () -> ResultValueConverters.toJapaneseDate("2026-09-06"))
        );
    }

    @Test
    void rejectsOffsetDateTimeConversionWithoutInstantOrOffset() {
        assertThrows(IllegalArgumentException.class, () ->
            ResultValueConverters.toOffsetDateTime(LocalDateTime.of(2026, 8, 31, 12, 34, 56)));
    }

    @Test
    void convertsAndValidatesEnumOrdinals() {
        assertEquals(ExampleStatus.DISABLED,
            ResultValueConverters.toEnumOrdinal(1, ExampleStatus.class));
        assertNull(ResultValueConverters.toEnumOrdinal(null, ExampleStatus.class));
        assertThrows(IllegalArgumentException.class,
            () -> ResultValueConverters.toEnumOrdinal(-1, ExampleStatus.class));
        assertThrows(IllegalArgumentException.class,
            () -> ResultValueConverters.toEnumOrdinal(2, ExampleStatus.class));
        assertThrows(IllegalArgumentException.class,
            () -> ResultValueConverters.toEnumOrdinal("1", ExampleStatus.class));
    }

    private static LocalDate dateTimeWithoutJdbcRepresentation() {
        return LocalDate.of(2026, 9, 6);
    }

    private enum ExampleStatus {
        ACTIVE,
        DISABLED
    }
}
