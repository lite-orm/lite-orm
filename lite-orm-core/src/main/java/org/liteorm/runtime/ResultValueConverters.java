package org.liteorm.runtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Month;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.chrono.JapaneseDate;
import java.util.UUID;

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

    /** Converts integral JDBC numeric values to {@link BigInteger}, truncating any fractional part. */
    public static BigInteger toBigInteger(Object value) {
        if (value == null) return null;
        if (value instanceof BigInteger integer) return integer;
        if (value instanceof BigDecimal decimal) return decimal.toBigInteger();
        if (value instanceof Number number) return new BigDecimal(number.toString()).toBigInteger();
        throw unsupported(value, BigInteger.class);
    }

    /** Converts primitive or boxed JDBC byte arrays to a defensive boxed copy. */
    public static Byte[] toBoxedBytes(Object value) {
        if (value == null) return null;
        if (value instanceof Byte[] boxed) return boxed.clone();
        if (value instanceof byte[] bytes) {
            Byte[] boxed = new Byte[bytes.length];
            for (int index = 0; index < bytes.length; index++) boxed[index] = bytes[index];
            return boxed;
        }
        throw unsupported(value, Byte[].class);
    }

    public static String toStringValue(Object value) {
        return value == null ? null : value.toString();
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

    public static UUID toUuid(Object value) {
        if (value == null) return null;
        if (value instanceof UUID uuid) return uuid;
        if (value instanceof String string) return UUID.fromString(string);
        throw unsupported(value, UUID.class);
    }

    public static LocalTime toLocalTime(Object value) {
        if (value == null) return null;
        if (value instanceof LocalTime localTime) return localTime;
        if (value instanceof Time time) return time.toLocalTime();
        throw unsupported(value, LocalTime.class);
    }

    public static OffsetDateTime toOffsetDateTime(Object value) {
        if (value == null) return null;
        if (value instanceof OffsetDateTime offsetDateTime) return offsetDateTime;
        if (value instanceof Instant instant) return instant.atOffset(ZoneOffset.UTC);
        if (value instanceof Timestamp timestamp) return timestamp.toInstant().atOffset(ZoneOffset.UTC);
        throw unsupported(value, OffsetDateTime.class);
    }

    /** Converts JDBC timestamp values to a defensive {@link java.util.Date} copy. */
    public static java.util.Date toUtilDate(Object value) {
        if (value == null) return null;
        if (value instanceof java.util.Date date) return new java.util.Date(date.getTime());
        if (value instanceof LocalDateTime dateTime) return Timestamp.valueOf(dateTime);
        throw unsupported(value, java.util.Date.class);
    }

    /** Converts JDBC, ISO local-date, or timestamp values to {@link Date}. */
    public static Date toSqlDate(Object value) {
        if (value == null) return null;
        if (value instanceof Date date) return date;
        if (value instanceof LocalDate localDate) return Date.valueOf(localDate);
        if (value instanceof Timestamp timestamp) return Date.valueOf(timestamp.toLocalDateTime().toLocalDate());
        throw unsupported(value, Date.class);
    }

    /** Converts JDBC or ISO local-time values to {@link Time}. */
    public static Time toSqlTime(Object value) {
        if (value == null) return null;
        if (value instanceof Time time) return time;
        if (value instanceof LocalTime localTime) return Time.valueOf(localTime);
        throw unsupported(value, Time.class);
    }

    /** Converts JDBC timestamp, local-date-time, or legacy date values to {@link Timestamp}. */
    public static Timestamp toSqlTimestamp(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp;
        if (value instanceof LocalDateTime dateTime) return Timestamp.valueOf(dateTime);
        if (value instanceof java.util.Date date) return new Timestamp(date.getTime());
        throw unsupported(value, Timestamp.class);
    }

    /** Converts integral JDBC numeric values to an ISO {@link Year}. */
    public static Year toYear(Object value) {
        if (value == null) return null;
        if (value instanceof Year year) return year;
        if (value instanceof Number number) return Year.of(number.intValue());
        throw unsupported(value, Year.class);
    }

    /** Converts JDBC month numbers from 1 through 12 to {@link Month}. */
    public static Month toMonth(Object value) {
        if (value == null) return null;
        if (value instanceof Month month) return month;
        if (value instanceof Number number) return Month.of(number.intValue());
        throw unsupported(value, Month.class);
    }

    /** Converts ISO {@code uuuu-MM} JDBC text to {@link YearMonth}. */
    public static YearMonth toYearMonth(Object value) {
        if (value == null) return null;
        if (value instanceof YearMonth yearMonth) return yearMonth;
        if (value instanceof String string) return YearMonth.parse(string);
        throw unsupported(value, YearMonth.class);
    }

    /** Converts ISO local-date or JDBC date values to {@link JapaneseDate}. */
    public static JapaneseDate toJapaneseDate(Object value) {
        if (value == null) return null;
        if (value instanceof JapaneseDate date) return date;
        if (value instanceof Date date) return JapaneseDate.from(date.toLocalDate());
        if (value instanceof LocalDate localDate) return JapaneseDate.from(localDate);
        throw unsupported(value, JapaneseDate.class);
    }

    /** Converts a zero-based JDBC numeric value to one constant of the supplied enum type. */
    public static <E extends Enum<E>> E toEnumOrdinal(Object value, Class<E> enumType) {
        if (value == null) return null;
        if (enumType.isInstance(value)) return enumType.cast(value);
        if (!(value instanceof Number number)) throw unsupported(value, enumType);
        E[] constants = enumType.getEnumConstants();
        int ordinal = number.intValue();
        if (ordinal < 0 || ordinal >= constants.length) {
            throw new IllegalArgumentException(
                "Enum ordinal " + ordinal + " is outside the declared constant range for " + enumType.getName());
        }
        return constants[ordinal];
    }

    private static IllegalArgumentException unsupported(Object value, Class<?> targetType) {
        return new IllegalArgumentException(
            "Cannot convert JDBC value " + value.getClass().getName() + " to " + targetType.getName());
    }
}
