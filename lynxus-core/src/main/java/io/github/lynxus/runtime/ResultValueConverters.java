package io.github.lynxus.runtime;

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
        return switch (value) {
            case null -> null;
            case BigInteger integer -> integer;
            case BigDecimal decimal -> decimal.toBigInteger();
            case Number number -> new BigDecimal(number.toString()).toBigInteger();
            default -> throw unsupported(value, BigInteger.class);
        };
    }

    /** Converts primitive or boxed JDBC byte arrays to a defensive boxed copy. */
    public static Byte[] toBoxedBytes(Object value) {
        switch (value) {
            case null -> {
                return null;
            }
            case Byte[] boxed -> {
                return boxed.clone();
            }
            case byte[] bytes -> {
                Byte[] boxed = new Byte[bytes.length];
                for (int index = 0; index < bytes.length; index++) boxed[index] = bytes[index];
                return boxed;
            }
            default -> {
            }
        }
        throw unsupported(value, Byte[].class);
    }

    public static String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    public static Boolean toBoolean(Object value) {
        return switch (value) {
            case null -> null;
            case Boolean bool -> bool;
            case Number number -> number.intValue() != 0;
            case String string -> Boolean.valueOf(string);
            default -> throw unsupported(value, Boolean.class);
        };
    }

    public static Character toCharacter(Object value) {
        if (value == null) return null;
        if (value instanceof Character character) return character;
        String string = value.toString();
        if (string.length() == 1) return string.charAt(0);
        throw unsupported(value, Character.class);
    }

    public static LocalDate toLocalDate(Object value) {
        return switch (value) {
            case null -> null;
            case LocalDate localDate -> localDate;
            case Date date -> date.toLocalDate();
            case Timestamp timestamp -> timestamp.toLocalDateTime().toLocalDate();
            default -> throw unsupported(value, LocalDate.class);
        };
    }

    public static LocalDateTime toLocalDateTime(Object value) {
        return switch (value) {
            case null -> null;
            case LocalDateTime localDateTime -> localDateTime;
            case Timestamp timestamp -> timestamp.toLocalDateTime();
            case OffsetDateTime offsetDateTime -> offsetDateTime.toLocalDateTime();
            default -> throw unsupported(value, LocalDateTime.class);
        };
    }

    public static Instant toInstant(Object value) {
        return switch (value) {
            case null -> null;
            case Instant instant -> instant;
            case Timestamp timestamp -> timestamp.toInstant();
            case OffsetDateTime offsetDateTime -> offsetDateTime.toInstant();
            default -> throw unsupported(value, Instant.class);
        };
    }

    public static UUID toUuid(Object value) {
        return switch (value) {
            case null -> null;
            case UUID uuid -> uuid;
            case String string -> UUID.fromString(string);
            default -> throw unsupported(value, UUID.class);
        };
    }

    public static LocalTime toLocalTime(Object value) {
        return switch (value) {
            case null -> null;
            case LocalTime localTime -> localTime;
            case Time time -> time.toLocalTime();
            default -> throw unsupported(value, LocalTime.class);
        };
    }

    public static OffsetDateTime toOffsetDateTime(Object value) {
        return switch (value) {
            case null -> null;
            case OffsetDateTime offsetDateTime -> offsetDateTime;
            case Instant instant -> instant.atOffset(ZoneOffset.UTC);
            case Timestamp timestamp -> timestamp.toInstant().atOffset(ZoneOffset.UTC);
            default -> throw unsupported(value, OffsetDateTime.class);
        };
    }

    /** Converts JDBC timestamp values to a defensive {@link java.util.Date} copy. */
    public static java.util.Date toUtilDate(Object value) {
        return switch (value) {
            case null -> null;
            case java.util.Date date -> new java.util.Date(date.getTime());
            case LocalDateTime dateTime -> Timestamp.valueOf(dateTime);
            default -> throw unsupported(value, java.util.Date.class);
        };
    }

    /** Converts JDBC, ISO local-date, or timestamp values to {@link Date}. */
    public static Date toSqlDate(Object value) {
        return switch (value) {
            case null -> null;
            case Date date -> date;
            case LocalDate localDate -> Date.valueOf(localDate);
            case Timestamp timestamp -> Date.valueOf(timestamp.toLocalDateTime().toLocalDate());
            default -> throw unsupported(value, Date.class);
        };
    }

    /** Converts JDBC or ISO local-time values to {@link Time}. */
    public static Time toSqlTime(Object value) {
        return switch (value) {
            case null -> null;
            case Time time -> time;
            case LocalTime localTime -> Time.valueOf(localTime);
            default -> throw unsupported(value, Time.class);
        };
    }

    /** Converts JDBC timestamp, local-date-time, or legacy date values to {@link Timestamp}. */
    public static Timestamp toSqlTimestamp(Object value) {
        return switch (value) {
            case null -> null;
            case Timestamp timestamp -> timestamp;
            case LocalDateTime dateTime -> Timestamp.valueOf(dateTime);
            case java.util.Date date -> new Timestamp(date.getTime());
            default -> throw unsupported(value, Timestamp.class);
        };
    }

    /** Converts integral JDBC numeric values to an ISO {@link Year}. */
    public static Year toYear(Object value) {
        return switch (value) {
            case null -> null;
            case Year year -> year;
            case Number number -> Year.of(number.intValue());
            default -> throw unsupported(value, Year.class);
        };
    }

    /** Converts JDBC month numbers from 1 through 12 to {@link Month}. */
    public static Month toMonth(Object value) {
        return switch (value) {
            case null -> null;
            case Month month -> month;
            case Number number -> Month.of(number.intValue());
            default -> throw unsupported(value, Month.class);
        };
    }

    /** Converts ISO {@code uuuu-MM} JDBC text to {@link YearMonth}. */
    public static YearMonth toYearMonth(Object value) {
        return switch (value) {
            case null -> null;
            case YearMonth yearMonth -> yearMonth;
            case String string -> YearMonth.parse(string);
            default -> throw unsupported(value, YearMonth.class);
        };
    }

    /** Converts ISO local-date or JDBC date values to {@link JapaneseDate}. */
    public static JapaneseDate toJapaneseDate(Object value) {
        return switch (value) {
            case null -> null;
            case JapaneseDate date -> date;
            case Date date -> JapaneseDate.from(date.toLocalDate());
            case LocalDate localDate -> JapaneseDate.from(localDate);
            default -> throw unsupported(value, JapaneseDate.class);
        };
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
