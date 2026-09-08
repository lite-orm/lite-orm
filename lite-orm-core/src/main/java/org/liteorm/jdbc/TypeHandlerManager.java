package org.liteorm.jdbc;

import org.liteorm.api.ParameterBinder;
import org.liteorm.api.TypeHandler;
import org.liteorm.runtime.ResultValueConverters;

import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSetMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Selects type handlers for JDBC parameters and results.
 *
 * <p>Instances are thread-safe when their configured handlers are thread-safe. Standard JDBC
 * values require no configured mappings. Result routing
 * prefers an exact vendor type name, then a generic Java/JDBC route, then a documented compatible
 * JDBC family. Missing and ambiguous routes fail with {@link SQLException}.</p>
 */
public final class TypeHandlerManager {

    private final Map<Key, TypeHandler<?>> handlers;

    /** Creates a manager for Core standard JDBC values. */
    public TypeHandlerManager() {
        this(List.of());
    }

    /**
     * Creates a manager with additional immutable mappings.
     *
     * @throws NullPointerException if the list, a mapping, or one of its required values is null
     * @throws IllegalArgumentException if two mappings declare the same route key
     */
    public TypeHandlerManager(List<Mapping<?>> mappings) {
        Objects.requireNonNull(mappings, "mappings");
        Map<Key, TypeHandler<?>> configured = new LinkedHashMap<>();
        for (Mapping<?> mapping : mappings) {
            Objects.requireNonNull(mapping, "mapping");
            Key key = new Key(
                box(mapping.javaType()), mapping.jdbcType(), normalizeVendorTypeName(mapping.vendorTypeName()));
            if (configured.putIfAbsent(key, mapping.handler()) != null) {
                throw new IllegalArgumentException(
                    "Duplicate type handler for " + key.javaType().getName() + " + " + key.jdbcType());
            }
        }
        this.handlers = Map.copyOf(configured);
    }

    /** Declares one Java/JDBC representation and its handler. */
    public static <T> Mapping<T> mapping(
            Class<T> javaType, JDBCType jdbcType, TypeHandler<T> handler) {
        return new Mapping<>(javaType, jdbcType, null, handler);
    }

    /** Declares one vendor-specific Java/JDBC representation and its handler. */
    public static <T> Mapping<T> mapping(
            Class<T> javaType,
            JDBCType jdbcType,
            String vendorTypeName,
            TypeHandler<T> handler) {
        return new Mapping<>(javaType, jdbcType, vendorTypeName, handler);
    }

    /**
     * Writes one parameter through the selected handler.
     *
     * @throws SQLException if the route is missing or ambiguous, or the selected handler fails
     */
    public void setParameter(
            PreparedStatement statement,
            int index,
            Object value,
            Class<?> javaType,
            JDBCType jdbcType) throws SQLException {
        JDBCType resolvedJdbcType;
        try {
            resolvedJdbcType = jdbcType == null ? defaultJdbcType(javaType) : jdbcType;
        } catch (SQLException exception) {
            throw new SQLException(
                "No default TypeHandler for " + box(javaType).getName()
                    + ". Use @UseParameterBinder for custom parameter conversion.",
                exception);
        }
        TypeHandler<Object> handler = resolveParameter(javaType, resolvedJdbcType);
        handler.setParameter(statement, index, value, resolvedJdbcType);
    }

    /**
     * Creates one reusable parameter binder from the generated Java type and optional JDBC type.
     * The binder is thread-safe when this manager and its handlers are thread-safe.
     */
    public <T> ParameterBinder<T> parameterBinder(Class<T> javaType, JDBCType jdbcType) {
        return (statement, index, value) -> setParameter(statement, index, value, javaType, jdbcType);
    }

    /**
     * Resolves one result handler from driver metadata and the generated Java target type.
     *
     * @throws SQLException if metadata is invalid, the route is missing or ambiguous, or metadata
     * access fails
     */
    public <T> TypeHandler<T> resolveResult(
            ResultSetMetaData metadata, int columnIndex, Class<T> javaType) throws SQLException {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(javaType, "javaType");
        int typeNumber = metadata.getColumnType(columnIndex);
        String vendorTypeName = metadata.getColumnTypeName(columnIndex);
        JDBCType jdbcType;
        try {
            jdbcType = JDBCType.valueOf(typeNumber);
        } catch (IllegalArgumentException exception) {
            throw new SQLException(
                "Driver reported unsupported JDBC type " + typeNumber
                    + " (" + vendorTypeName + ") for " + resultColumn(metadata, columnIndex)
                    + " targeting " + javaType.getName() + ". "
                    + "Use @UseRowMapper for custom result conversion.",
                exception);
        }
        try {
            return resolveResult(javaType, jdbcType, vendorTypeName);
        } catch (SQLException exception) {
            throw new SQLException(
                exception.getMessage() + " at " + resultColumn(metadata, columnIndex)
                    + " (vendor type " + vendorTypeName + "). "
                    + "Add a matching @JdbcTypeMapping TypeHandler or use @UseRowMapper.",
                exception);
        }
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

    @SuppressWarnings("unchecked")
    private <T> TypeHandler<T> resolveParameter(Class<?> javaType, JDBCType jdbcType) throws SQLException {
        Objects.requireNonNull(javaType, "javaType");
        Objects.requireNonNull(jdbcType, "jdbcType");
        Class<?> boxedType = box(javaType);
        TypeHandler<?> handler = handlers.get(new Key(boxedType, jdbcType, null));
        if (handler == null) {
            List<TypeHandler<?>> vendorHandlers = handlers.entrySet().stream()
                .filter(entry -> entry.getKey().javaType().equals(boxedType))
                .filter(entry -> entry.getKey().jdbcType() == jdbcType)
                .map(Map.Entry::getValue)
                .toList();
            if (vendorHandlers.size() == 1) {
                handler = vendorHandlers.getFirst();
            } else if (vendorHandlers.size() > 1) {
                throw new SQLException(
                    "Ambiguous TypeHandlers for " + boxedType.getName() + " + " + jdbcType
                        + "; declare a generic route or use @UseParameterBinder");
            }
        }
        if (handler == null && supportsStandardType(boxedType, jdbcType)) {
            handler = new StandardTypeHandler<>(boxedType, jdbcType);
        }
        if (handler == null) {
            throw new SQLException(
                "No TypeHandler for " + boxedType.getName() + " + " + jdbcType
                    + ". Use @UseParameterBinder for custom parameter conversion.");
        }
        return (TypeHandler<T>) handler;
    }

    @SuppressWarnings("unchecked")
    private <T> TypeHandler<T> resolveResult(
            Class<?> javaType, JDBCType jdbcType, String vendorTypeName) throws SQLException {
        Objects.requireNonNull(javaType, "javaType");
        Objects.requireNonNull(jdbcType, "jdbcType");
        Class<?> boxedType = box(javaType);
        String normalizedVendorType = normalizeVendorTypeName(vendorTypeName);
        TypeHandler<?> handler = normalizedVendorType == null
            ? null : handlers.get(new Key(boxedType, jdbcType, normalizedVendorType));
        if (handler == null) {
            handler = handlers.get(new Key(boxedType, jdbcType, null));
        }
        if (handler == null) {
            handler = compatibleHandler(boxedType, jdbcType);
        }
        if (handler == null && supportsStandardType(boxedType, jdbcType)) {
            handler = new StandardTypeHandler<>(boxedType, jdbcType);
        }
        if (handler == null) {
            throw new SQLException(
                "No TypeHandler for " + boxedType.getName() + " + " + jdbcType);
        }
        return (TypeHandler<T>) handler;
    }

    private TypeHandler<?> compatibleHandler(Class<?> javaType, JDBCType reportedType) throws SQLException {
        JDBCType[] compatibleTypes = switch (reportedType) {
            case BINARY -> new JDBCType[]{JDBCType.VARBINARY, JDBCType.LONGVARBINARY};
            case VARBINARY -> new JDBCType[]{JDBCType.BINARY, JDBCType.LONGVARBINARY};
            case LONGVARBINARY -> new JDBCType[]{JDBCType.VARBINARY, JDBCType.BINARY};
            case TIME -> new JDBCType[]{JDBCType.TIME_WITH_TIMEZONE};
            case TIMESTAMP -> new JDBCType[]{JDBCType.TIMESTAMP_WITH_TIMEZONE};
            default -> new JDBCType[0];
        };
        List<CompatibleHandler> candidates = new ArrayList<>();
        for (JDBCType compatibleType : compatibleTypes) {
            TypeHandler<?> handler = handlers.get(new Key(javaType, compatibleType, null));
            if (handler != null) {
                candidates.add(new CompatibleHandler(compatibleType, handler));
            }
        }
        if (candidates.size() > 1) {
            String routes = candidates.stream()
                .map(candidate -> candidate.jdbcType().name())
                .collect(java.util.stream.Collectors.joining(", "));
            throw new SQLException(
                "Ambiguous TypeHandlers for " + javaType.getName() + " + " + reportedType
                    + "; compatible routes are [" + routes + "]");
        }
        return candidates.isEmpty() ? null : candidates.getFirst().handler();
    }

    private static String normalizeVendorTypeName(String vendorTypeName) {
        return vendorTypeName == null || vendorTypeName.isBlank()
            ? null : vendorTypeName.trim().toLowerCase(Locale.ROOT);
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
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

    private static boolean supportsStandardType(Class<?> type, JDBCType jdbcType) {
        if (Number.class.isAssignableFrom(type)
                || type == java.time.Year.class || type == java.time.Month.class) {
            return isNumeric(jdbcType);
        }
        if (type == Boolean.class) {
            return jdbcType == JDBCType.BOOLEAN || jdbcType == JDBCType.BIT || isNumeric(jdbcType);
        }
        if (type == Character.class || type == String.class || type == java.time.YearMonth.class) {
            return isCharacter(jdbcType);
        }
        if (type == byte[].class) {
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
                || type == java.util.Date.class || type == java.sql.Timestamp.class) {
            return jdbcType == JDBCType.TIMESTAMP || jdbcType == JDBCType.TIMESTAMP_WITH_TIMEZONE;
        }
        if (type == java.time.OffsetDateTime.class) {
            return jdbcType == JDBCType.TIMESTAMP || jdbcType == JDBCType.TIMESTAMP_WITH_TIMEZONE;
        }
        return type == java.util.UUID.class
            && (jdbcType == JDBCType.OTHER || isCharacter(jdbcType));
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

    private static JDBCType defaultJdbcType(Class<?> javaType) throws SQLException {
        Class<?> type = box(javaType);
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
        if (type == String.class || type == java.time.YearMonth.class) {
            return JDBCType.VARCHAR;
        }
        if (type == java.math.BigDecimal.class || type == java.math.BigInteger.class) {
            return JDBCType.DECIMAL;
        }
        if (type == java.time.LocalDate.class || type == java.sql.Date.class
                || type == java.time.chrono.JapaneseDate.class) {
            return JDBCType.DATE;
        }
        if (type == java.time.LocalTime.class || type == java.sql.Time.class) return JDBCType.TIME;
        if (type == java.time.LocalDateTime.class || type == java.time.Instant.class
                || type == java.util.Date.class || type == java.sql.Timestamp.class) {
            return JDBCType.TIMESTAMP;
        }
        if (type == java.time.OffsetDateTime.class) return JDBCType.TIMESTAMP_WITH_TIMEZONE;
        if (type == java.util.UUID.class) return JDBCType.OTHER;
        if (type == byte[].class) return JDBCType.VARBINARY;
        throw new SQLException("No default JDBC type for " + type.getName());
    }

    private static final class StandardTypeHandler<T> implements TypeHandler<T> {

        private final Class<?> javaType;
        private final JDBCType jdbcType;

        private StandardTypeHandler(Class<?> javaType, JDBCType jdbcType) {
            this.javaType = javaType;
            this.jdbcType = jdbcType;
        }

        @Override
        public void setNonNull(
                PreparedStatement statement, int index, T value, JDBCType jdbcType) throws SQLException {
            if (javaType == String.class) {
                statement.setString(index, (String) value);
            } else if (javaType == byte[].class) {
                statement.setBytes(index, (byte[]) value);
            } else if (javaType == java.util.UUID.class && isCharacter(jdbcType)) {
                statement.setString(index, value.toString());
            } else {
                statement.setObject(index, value);
            }
        }

        @Override
        @SuppressWarnings("unchecked")
        public T getResult(ResultSet resultSet, int columnIndex) throws SQLException {
            Object value = javaType == java.time.LocalTime.class
                ? resultSet.getObject(columnIndex, java.time.LocalTime.class)
                : resultSet.getObject(columnIndex);
            Object converted;
            if (javaType == String.class) converted = ResultValueConverters.toStringValue(value);
            else if (javaType == Long.class) converted = ResultValueConverters.toLong(value);
            else if (javaType == Integer.class) converted = ResultValueConverters.toInteger(value);
            else if (javaType == Short.class) converted = ResultValueConverters.toShort(value);
            else if (javaType == Byte.class) converted = ResultValueConverters.toByte(value);
            else if (javaType == Double.class) converted = ResultValueConverters.toDouble(value);
            else if (javaType == Float.class) converted = ResultValueConverters.toFloat(value);
            else if (javaType == java.math.BigDecimal.class) converted = ResultValueConverters.toBigDecimal(value);
            else if (javaType == java.math.BigInteger.class) converted = ResultValueConverters.toBigInteger(value);
            else if (javaType == Boolean.class) converted = ResultValueConverters.toBoolean(value);
            else if (javaType == Character.class) converted = ResultValueConverters.toCharacter(value);
            else if (javaType == java.time.LocalDate.class) converted = ResultValueConverters.toLocalDate(value);
            else if (javaType == java.time.LocalDateTime.class) converted = ResultValueConverters.toLocalDateTime(value);
            else if (javaType == java.time.Instant.class) converted = ResultValueConverters.toInstant(value);
            else if (javaType == java.util.UUID.class) converted = ResultValueConverters.toUuid(value);
            else if (javaType == java.time.LocalTime.class) converted = ResultValueConverters.toLocalTime(value);
            else if (javaType == java.time.OffsetDateTime.class) converted = ResultValueConverters.toOffsetDateTime(value);
            else if (javaType == byte[].class) converted = value;
            else if (javaType == java.util.Date.class) converted = ResultValueConverters.toUtilDate(value);
            else if (javaType == java.sql.Date.class) converted = ResultValueConverters.toSqlDate(value);
            else if (javaType == java.sql.Time.class) converted = ResultValueConverters.toSqlTime(value);
            else if (javaType == java.sql.Timestamp.class) converted = ResultValueConverters.toSqlTimestamp(value);
            else if (javaType == java.time.Year.class) converted = ResultValueConverters.toYear(value);
            else if (javaType == java.time.Month.class) converted = ResultValueConverters.toMonth(value);
            else if (javaType == java.time.YearMonth.class) converted = ResultValueConverters.toYearMonth(value);
            else if (javaType == java.time.chrono.JapaneseDate.class) {
                converted = ResultValueConverters.toJapaneseDate(value);
            } else {
                converted = value;
            }
            return (T) converted;
        }

    }

    /**
     * One immutable route entry. A null or blank vendor type name declares a generic JDBC route.
     */
    public record Mapping<T>(
            Class<T> javaType, JDBCType jdbcType, String vendorTypeName, TypeHandler<T> handler) {

        public Mapping {
            Objects.requireNonNull(javaType, "javaType");
            Objects.requireNonNull(jdbcType, "jdbcType");
            vendorTypeName = normalizeVendorTypeName(vendorTypeName);
            Objects.requireNonNull(handler, "handler");
        }
    }

    private record Key(Class<?> javaType, JDBCType jdbcType, String vendorTypeName) {
    }

    private record CompatibleHandler(JDBCType jdbcType, TypeHandler<?> handler) {
    }
}
