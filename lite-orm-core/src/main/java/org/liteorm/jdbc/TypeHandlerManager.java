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
 *
 * <p>Instances are stateless and thread-safe. {@link JdbcSqlExecutor} owns one instance across
 * concurrent executions; generated Mappers carry routing metadata only.</p>
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

    /**
     * Creates a thread-safe reusable binder for one generated Java/JDBC route.
     *
     * @param javaType generated parameter type; must not be {@code null}
     * @param jdbcType declared JDBC type, or {@code null} to use the Core default for
     *     {@code javaType}
     * @param <T> parameter value type
     * @return a binder that reports unsupported routes and JDBC failures as {@link SQLException}
     * @throws NullPointerException if {@code javaType} is {@code null}
     */
    public <T> ParameterBinder<T> parameterBinder(Class<T> javaType, JDBCType jdbcType) {
        Class<T> requiredJavaType = Objects.requireNonNull(javaType, "javaType");
        return (statement, index, value) ->
            setParameter(statement, index, value, requiredJavaType, jdbcType);
    }

    <T> ResultHandler<T> resolveResult(
            ResultSetMetaData metadata, int columnIndex, Class<T> javaType) throws SQLException {
        Objects.requireNonNull(metadata, "metadata");
        return resolveResult(metadata, columnIndex, metadata.getColumnType(columnIndex), javaType);
    }

    <T> ResultHandler<T> resolveResult(
            ResultSetMetaData metadata,
            int columnIndex,
            int typeNumber,
            Class<T> javaType) throws SQLException {
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
        return resultHandler(jdbcType, targetType);
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
            long time = ((java.util.Date) value).getTime();
            if (jdbcType == JDBCType.DATE) {
                statement.setDate(index, new java.sql.Date(time));
            } else if (jdbcType == JDBCType.TIME) {
                statement.setTime(index, new java.sql.Time(time));
            } else {
                statement.setTimestamp(index, new java.sql.Timestamp(time));
            }
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

    private <T> ResultHandler<T> resultHandler(JDBCType jdbcType, Class<?> javaType) {
        ResultValueReader reader = resultValueReader(jdbcType, javaType);
        ResultHandler<?> handler;
        if (javaType == String.class) handler = converting(reader, ResultValueConverters::toStringValue);
        else if (javaType == Long.class) handler = converting(reader, ResultValueConverters::toLong);
        else if (javaType == Integer.class) handler = converting(reader, ResultValueConverters::toInteger);
        else if (javaType == Short.class) handler = converting(reader, ResultValueConverters::toShort);
        else if (javaType == Byte.class) handler = converting(reader, ResultValueConverters::toByte);
        else if (javaType == Double.class) handler = converting(reader, ResultValueConverters::toDouble);
        else if (javaType == Float.class) handler = converting(reader, ResultValueConverters::toFloat);
        else if (javaType == java.math.BigDecimal.class) handler = converting(reader, ResultValueConverters::toBigDecimal);
        else if (javaType == java.math.BigInteger.class) handler = converting(reader, ResultValueConverters::toBigInteger);
        else if (javaType == Boolean.class) handler = converting(reader, ResultValueConverters::toBoolean);
        else if (javaType == Character.class) handler = converting(reader, ResultValueConverters::toCharacter);
        else if (javaType == java.time.LocalDate.class) handler = converting(reader, ResultValueConverters::toLocalDate);
        else if (javaType == java.time.LocalDateTime.class) handler = converting(reader, ResultValueConverters::toLocalDateTime);
        else if (javaType == java.time.Instant.class) handler = converting(reader, ResultValueConverters::toInstant);
        else if (javaType == java.util.UUID.class) handler = converting(reader, ResultValueConverters::toUuid);
        else if (javaType == java.time.LocalTime.class) handler = converting(reader, ResultValueConverters::toLocalTime);
        else if (javaType == java.time.OffsetDateTime.class) handler = converting(reader, ResultValueConverters::toOffsetDateTime);
        else if (javaType == byte[].class) handler = converting(reader, value -> (byte[]) value);
        else if (javaType == Byte[].class) handler = converting(reader, ResultValueConverters::toBoxedBytes);
        else if (javaType == java.util.Date.class) handler = converting(reader, ResultValueConverters::toUtilDate);
        else if (javaType == java.sql.Date.class) handler = converting(reader, ResultValueConverters::toSqlDate);
        else if (javaType == java.sql.Time.class) handler = converting(reader, ResultValueConverters::toSqlTime);
        else if (javaType == java.sql.Timestamp.class) handler = converting(reader, ResultValueConverters::toSqlTimestamp);
        else if (javaType == java.time.Year.class) handler = converting(reader, ResultValueConverters::toYear);
        else if (javaType == java.time.Month.class) handler = converting(reader, ResultValueConverters::toMonth);
        else if (javaType == java.time.YearMonth.class) handler = converting(reader, ResultValueConverters::toYearMonth);
        else if (javaType == java.time.chrono.JapaneseDate.class) handler = converting(reader, ResultValueConverters::toJapaneseDate);
        else if (javaType.isEnum()) handler = enumResultHandler(jdbcType, javaType, reader);
        else throw new IllegalStateException("Validated result route has no handler for " + javaType.getName());
        return castHandler(handler);
    }

    private ResultValueReader resultValueReader(JDBCType jdbcType, Class<?> javaType) {
        if (javaType.isEnum() && javaType != java.time.Month.class) {
            return isNumeric(jdbcType) ? ResultSet::getObject : ResultSet::getString;
        }
        if (javaType == String.class || javaType == Character.class
                || javaType == java.time.YearMonth.class) {
            return ResultSet::getString;
        }
        if (javaType == byte[].class || javaType == Byte[].class) {
            return ResultSet::getBytes;
        }
        if (javaType == java.time.LocalDate.class || javaType == java.sql.Date.class
                || javaType == java.time.chrono.JapaneseDate.class
                || javaType == java.util.Date.class && jdbcType == JDBCType.DATE) {
            return ResultSet::getDate;
        }
        if (javaType == java.time.LocalTime.class) {
            return (resultSet, columnIndex) ->
                resultSet.getObject(columnIndex, java.time.LocalTime.class);
        }
        if (javaType == java.sql.Time.class
                || javaType == java.util.Date.class && jdbcType == JDBCType.TIME) {
            return ResultSet::getTime;
        }
        if (javaType == java.time.LocalDateTime.class || javaType == java.time.Instant.class
                || javaType == java.sql.Timestamp.class
                || javaType == java.util.Date.class
                    && (jdbcType == JDBCType.TIMESTAMP
                        || jdbcType == JDBCType.TIMESTAMP_WITH_TIMEZONE)
                || javaType == java.time.OffsetDateTime.class && jdbcType == JDBCType.TIMESTAMP) {
            return ResultSet::getTimestamp;
        }
        if (javaType == java.time.OffsetDateTime.class
                && jdbcType == JDBCType.TIMESTAMP_WITH_TIMEZONE) {
            return (resultSet, columnIndex) ->
                resultSet.getObject(columnIndex, java.time.OffsetDateTime.class);
        }
        if (javaType == java.util.UUID.class && isCharacter(jdbcType)) {
            return ResultSet::getString;
        }
        return ResultSet::getObject;
    }

    private <T> ResultHandler<T> converting(
            ResultValueReader reader, ResultValueConverter<T> converter) {
        return (resultSet, columnIndex) -> converter.convert(reader.read(resultSet, columnIndex));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private ResultHandler<?> enumResultHandler(
            JDBCType jdbcType, Class<?> javaType, ResultValueReader reader) {
        Class<? extends Enum> enumType = javaType.asSubclass(Enum.class);
        if (isNumeric(jdbcType)) {
            return converting(reader, value -> ResultValueConverters.toEnumOrdinal(value, enumType));
        }
        return converting(reader, value -> value == null ? null : Enum.valueOf(enumType, value.toString()));
    }

    @SuppressWarnings("unchecked")
    private <T> ResultHandler<T> castHandler(ResultHandler<?> handler) {
        return (ResultHandler<T>) handler;
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
        if (type == java.util.Date.class) {
            return jdbcType == JDBCType.DATE || jdbcType == JDBCType.TIME
                || jdbcType == JDBCType.TIMESTAMP || jdbcType == JDBCType.TIMESTAMP_WITH_TIMEZONE;
        }
        if (type == java.time.LocalDateTime.class || type == java.time.Instant.class
                || type == java.sql.Timestamp.class
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
    interface ResultHandler<T> {
        T getResult(ResultSet resultSet, int columnIndex) throws SQLException;
    }

    @FunctionalInterface
    private interface ResultValueReader {
        Object read(ResultSet resultSet, int columnIndex) throws SQLException;
    }

    @FunctionalInterface
    private interface ResultValueConverter<T> {
        T convert(Object value);
    }
}
