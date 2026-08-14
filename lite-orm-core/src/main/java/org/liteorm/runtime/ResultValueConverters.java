package org.liteorm.runtime;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * Null-safe conversions from JDBC driver values to generated Mapper result types.
 */
public final class ResultValueConverters {

    private ResultValueConverters() {
    }

    public static Long toLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    public static Integer toInteger(Object value) {
        return value == null ? null : ((Number) value).intValue();
    }

    public static Short toShort(Object value) {
        return value == null ? null : ((Number) value).shortValue();
    }

    public static Byte toByte(Object value) {
        return value == null ? null : ((Number) value).byteValue();
    }

    public static Double toDouble(Object value) {
        return value == null ? null : ((Number) value).doubleValue();
    }

    public static Float toFloat(Object value) {
        return value == null ? null : ((Number) value).floatValue();
    }

    public static BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal;
        if (value instanceof Number number) return new BigDecimal(number.toString());
        throw unsupported(value, BigDecimal.class);
    }

    public static Boolean toBoolean(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean bool) return bool;
        if (value instanceof Number number) return number.intValue() != 0;
        if (value instanceof String string) return Boolean.valueOf(string);
        throw unsupported(value, Boolean.class);
    }

    public static Character toCharacter(Object value) {
        if (value == null) return null;
        if (value instanceof Character character) return character;
        String string = value.toString();
        if (string.length() == 1) return string.charAt(0);
        throw unsupported(value, Character.class);
    }

    public static LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate localDate) return localDate;
        if (value instanceof Date date) return date.toLocalDate();
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate();
        throw unsupported(value, LocalDate.class);
    }

    public static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime localDateTime) return localDateTime;
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime();
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toLocalDateTime();
        throw unsupported(value, LocalDateTime.class);
    }

    public static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime.toInstant();
        throw unsupported(value, Instant.class);
    }

    private static IllegalArgumentException unsupported(Object value, Class<?> targetType) {
        return new IllegalArgumentException(
            "Cannot convert JDBC value " + value.getClass().getName() + " to " + targetType.getName());
    }
}
