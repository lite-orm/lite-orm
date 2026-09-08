package org.liteorm.jdbc;

import org.liteorm.api.ParameterBinder;
import org.liteorm.runtime.ResultValueConverters;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Routes standard JDBC values between generated Java types and JDBC statements or result sets.
 *
 * <p>The supported routes are fixed by Core. Applications use {@code ParameterBinder} for a
 * parameter-specific write conversion and {@code RowMapper} for a method-specific read
 * conversion.</p>
 */
public final class TypeHandlerManager {

    /** Creates a manager for the fixed Core JDBC routes. */
    public TypeHandlerManager() {
    }

    /** Writes one generated parameter using its Java type and optional declared JDBC type. */
    void setParameter(
            PreparedStatement statement,
            int index,
            Object value,
            Class<?> javaType,
            JDBCType jdbcType) throws SQLException {
        Objects.requireNonNull(statement, "statement");
        Class<?> targetType = box(Objects.requireNonNull(javaType, "javaType"));
        JDBCType targetJdbcType = jdbcType == null ? defaultJdbcType(targetType) : jdbcType;
        if (!supports(targetType, targetJdbcType)) {
            throw new SQLException(
                "No standard TypeHandler for " + targetType.getName() + " + " + targetJdbcType
                    + ". Use @UseParameterBinder for custom parameter conversion.");
        }
        if (value == null) {
            statement.setNull(index, targetJdbcType.getVendorTypeNumber());
            return;
        }
        writeNonNull(statement, index, value, targetType, targetJdbcType);
    }

    /** Creates a reusable binder for one generated Java/JDBC route. */
    public <T> ParameterBinder<T> parameterBinder(Class<T> javaType, JDBCType jdbcType) {
        return (statement, index, value) -> setParameter(statement, index, value, javaType, jdbcType);
    }

    ResultHandler resolveResult(
            ResultSetMetaData metadata, int columnIndex, Class<?> javaType) throws SQLException {
        Objects.requireNonNull(metadata, "metadata");
        return resolveResult(metadata, columnIndex, metadata.getColumnType(columnIndex), javaType);
    }

    ResultHandler resolveResult(
            ResultSetMetaData metadata,
            int columnIndex,
            int typeNumber,
            Class<?> javaType) throws SQLException {
        Objects.requireNonNull(metadata, "metadata");
        Class<?> targetType = box(Objects.requireNonNull(javaType, "javaType"));
        JDBCType jdbcType;
        try {
            jdbcType = JDBCType.valueOf(typeNumber);
        } catch (IllegalArgumentException exception) {
            throw unsupportedResult(metadata, columnIndex, targetType,
                "driver JDBC type " + typeNumber, exception);
        }
        if (!supports(targetType, jdbcType)) {
            throw unsupportedResult(metadata, columnIndex, targetType, jdbcType.toString(), null);
        }
        return (resultSet, index) -> read(resultSet, index, targetType);
    }

    private SQLException unsupportedResult(
            ResultSetMetaData metadata,
            int columnIndex,
            Class<?> javaType,
            String jdbcType,
            Exception cause) throws SQLException {
        String message = "No standard TypeHandler for " + javaType.getName() + " + " + jdbcType
            + " at " + resultColumn(metadata, columnIndex)
            + ". Use @UseRowMapper for custom result conversion.";
        return cause == null ? new SQLException(message) : new SQLException(message, cause);
    }

    private String resultColumn(ResultSetMetaData metadata, int columnIndex) throws SQLException {
        String label = metadata.getColumnLabel(columnIndex);
        if (label == null || label.isBlank()) {
            label = metadata.getColumnName(columnIndex);
        }
        return label == null || label.isBlank()
            ? "result column " + columnIndex
            : "result column " + columnIndex + " '" + label + "'";
    }

    private void writeNonNull(
            PreparedStatement statement,
            int index,
            Object value,
            Class<?> javaType,
            JDBCType jdbcType) throws SQLException {
        if (javaType == String.class || javaType == Character.class
                || javaType == java.time.YearMonth.class) {
            statement.setString(index, value.toString());
        } else if (javaType == byte[].class) {
            statement.setBytes(index, (byte[]) value);
        } else if (javaType == Byte[].class) {
            statement.setBytes(index, toPrimitiveBytes((Byte[]) value));
        } else if (javaType == java.math.BigInteger.class) {
            statement.setBigDecimal(index, new java.math.BigDecimal((java.math.BigInteger) value));
        } else if (javaType == java.util.Date.class) {
            statement.setTimestamp(index, new java.sql.Timestamp(((java.util.Date) value).getTime()));
        } else if (javaType == java.time.Year.class) {
            statement.setInt(index, ((java.time.Year) value).getValue());
        } else if (javaType == java.time.Month.class) {
            statement.setInt(index, ((java.time.Month) value).getValue());
        } else if (javaType == java.time.chrono.JapaneseDate.class) {
            statement.setDate(index,
                java.sql.Date.valueOf(java.time.LocalDate.from((java.time.chrono.JapaneseDate) value)));
        } else if (javaType == java.util.UUID.class && isCharacter(jdbcType)) {
            statement.setString(index, value.toString());
        } else if (javaType.isEnum()) {
            Enum<?> enumValue = (Enum<?>) value;
            if (isNumeric(jdbcType)) {
                statement.setInt(index, enumValue.ordinal());
            } else {
                statement.setString(index, enumValue.name());
            }
        } else {
            statement.setObject(index, value);
        }
    }

    private Object read(ResultSet resultSet, int columnIndex, Class<?> javaType) throws SQLException {
        Object value = javaType == java.time.LocalTime.class
            ? resultSet.getObject(columnIndex, java.time.LocalTime.class)
            : resultSet.getObject(columnIndex);
        if (javaType.isEnum()) return value;
        if (javaType == String.class) return ResultValueConverters.toStringValue(value);
        if (javaType == Long.class) return ResultValueConverters.toLong(value);
        if (javaType == Integer.class) return ResultValueConverters.toInteger(value);
        if (javaType == Short.class) return ResultValueConverters.toShort(value);
        if (javaType == Byte.class) return ResultValueConverters.toByte(value);
        if (javaType == Double.class) return ResultValueConverters.toDouble(value);
        if (javaType == Float.class) return ResultValueConverters.toFloat(value);
        if (javaType == java.math.BigDecimal.class) return ResultValueConverters.toBigDecimal(value);
        if (javaType == java.math.BigInteger.class) return ResultValueConverters.toBigInteger(value);
        if (javaType == Boolean.class) return ResultValueConverters.toBoolean(value);
        if (javaType == Character.class) return ResultValueConverters.toCharacter(value);
        if (javaType == java.time.LocalDate.class) return ResultValueConverters.toLocalDate(value);
        if (javaType == java.time.LocalDateTime.class) return ResultValueConverters.toLocalDateTime(value);
        if (javaType == java.time.Instant.class) return ResultValueConverters.toInstant(value);
        if (javaType == java.util.UUID.class) return ResultValueConverters.toUuid(value);
        if (javaType == java.time.LocalTime.class) return ResultValueConverters.toLocalTime(value);
        if (javaType == java.time.OffsetDateTime.class) return ResultValueConverters.toOffsetDateTime(value);
        if (javaType == byte[].class) return value;
        if (javaType == Byte[].class) return ResultValueConverters.toBoxedBytes(value);
        if (javaType == java.util.Date.class) return ResultValueConverters.toUtilDate(value);
        if (javaType == java.sql.Date.class) return ResultValueConverters.toSqlDate(value);
        if (javaType == java.sql.Time.class) return ResultValueConverters.toSqlTime(value);
        if (javaType == java.sql.Timestamp.class) return ResultValueConverters.toSqlTimestamp(value);
        if (javaType == java.time.Year.class) return ResultValueConverters.toYear(value);
        if (javaType == java.time.Month.class) return ResultValueConverters.toMonth(value);
        if (javaType == java.time.YearMonth.class) return ResultValueConverters.toYearMonth(value);
        if (javaType == java.time.chrono.JapaneseDate.class) return ResultValueConverters.toJapaneseDate(value);
        return value;
    }

    private static boolean supports(Class<?> type, JDBCType jdbcType) {
        if (Number.class.isAssignableFrom(type)
                || type == java.time.Year.class || type == java.time.Month.class) {
            return isNumeric(jdbcType);
        }
        if (type.isEnum()) return isCharacter(jdbcType) || isNumeric(jdbcType);
        if (type == Boolean.class) {
            return jdbcType == JDBCType.BOOLEAN || jdbcType == JDBCType.BIT || isNumeric(jdbcType);
        }
        if (type == Character.class || type == String.class || type == java.time.YearMonth.class) {
            return isCharacter(jdbcType);
        }
        if (type == byte[].class || type == Byte[].class) {
            return jdbcType == JDBCType.BINARY
                || jdbcType == JDBCType.VARBINARY || jdbcType == JDBCType.LONGVARBINARY;
        }
        if (type == java.time.LocalDate.class || type == java.sql.Date.class
                || type == java.time.chrono.JapaneseDate.class) {
            return jdbcType == JDBCType.DATE;
        }
        if (type == java.time.LocalTime.class || type == java.sql.Time.class) {
            return jdbcType == JDBCType.TIME;
        }
        if (type == java.time.LocalDateTime.class || type == java.time.Instant.class
                || type == java.util.Date.class || type == java.sql.Timestamp.class
                || type == java.time.OffsetDateTime.class) {
            return jdbcType == JDBCType.TIMESTAMP || jdbcType == JDBCType.TIMESTAMP_WITH_TIMEZONE;
        }
        return type == java.util.UUID.class
            && (jdbcType == JDBCType.OTHER || isCharacter(jdbcType));
    }

    private static JDBCType defaultJdbcType(Class<?> javaType) throws SQLException {
        Class<?> type = box(javaType);
        if (type.isEnum()) return JDBCType.VARCHAR;
        if (type == Byte.class) return JDBCType.TINYINT;
        if (type == Short.class) return JDBCType.SMALLINT;
        if (type == Integer.class || type == java.time.Year.class || type == java.time.Month.class) {
            return JDBCType.INTEGER;
        }
        if (type == Long.class) return JDBCType.BIGINT;
        if (type == Float.class) return JDBCType.FLOAT;
        if (type == Double.class) return JDBCType.DOUBLE;
        if (type == Boolean.class) return JDBCType.BOOLEAN;
        if (type == Character.class) return JDBCType.CHAR;
        if (type == String.class || type == java.time.YearMonth.class) return JDBCType.VARCHAR;
        if (type == java.math.BigDecimal.class || type == java.math.BigInteger.class) return JDBCType.DECIMAL;
        if (type == java.time.LocalDate.class || type == java.sql.Date.class
                || type == java.time.chrono.JapaneseDate.class) return JDBCType.DATE;
        if (type == java.time.LocalTime.class || type == java.sql.Time.class) return JDBCType.TIME;
        if (type == java.time.LocalDateTime.class || type == java.time.Instant.class
                || type == java.util.Date.class || type == java.sql.Timestamp.class) return JDBCType.TIMESTAMP;
        if (type == java.time.OffsetDateTime.class) return JDBCType.TIMESTAMP_WITH_TIMEZONE;
        if (type == java.util.UUID.class) return JDBCType.OTHER;
        if (type == byte[].class || type == Byte[].class) return JDBCType.VARBINARY;
        throw new SQLException(
            "No standard TypeHandler for " + type.getName()
                + ". Use @UseParameterBinder for custom parameter conversion.");
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private static boolean isNumeric(JDBCType type) {
        return switch (type) {
            case TINYINT, SMALLINT, INTEGER, BIGINT, NUMERIC, DECIMAL, REAL, FLOAT, DOUBLE -> true;
            default -> false;
        };
    }

    private static boolean isCharacter(JDBCType type) {
        return switch (type) {
            case CHAR, VARCHAR, LONGVARCHAR, NCHAR, NVARCHAR, LONGNVARCHAR -> true;
            default -> false;
        };
    }

    private static byte[] toPrimitiveBytes(Byte[] value) {
        byte[] bytes = new byte[value.length];
        for (int index = 0; index < value.length; index++) {
            bytes[index] = value[index];
        }
        return bytes;
    }

    @FunctionalInterface
    interface ResultHandler {
        Object getResult(ResultSet resultSet, int columnIndex) throws SQLException;
    }
}
