package io.github.kervix.api;

import java.sql.JDBCType;
import java.util.Objects;

/** One ordered SQL-provider parameter with either routing metadata or an explicit binder. */
public record BoundParameter<T>(
        T value,
        ParameterBinder<? super T> binder,
        Class<?> javaType,
        JDBCType jdbcType) {

    public BoundParameter {
        if (binder == null && javaType == null) {
            throw new ConfigurationException(
                "Routed provider parameters require BoundParameter.of(javaType, value)");
        }
        if (value != null && javaType != null && !box(javaType).isInstance(value)) {
            throw new ConfigurationException(
                "Provider parameter value " + value.getClass().getName()
                    + " does not match declared Java type " + javaType.getName());
        }
    }

    /** Preserves the direct construction form for values with an explicit binder. */
    public BoundParameter(T value, ParameterBinder<? super T> binder) {
        this(value, Objects.requireNonNull(binder, "binder"), null, null);
    }

    /** Creates a routed provider parameter with its declared Java type. */
    public static <T> BoundParameter<T> of(Class<T> javaType, T value) {
        return of(javaType, value, null);
    }

    /** Creates a routed provider parameter with an explicit JDBC representation. */
    public static <T> BoundParameter<T> of(Class<T> javaType, T value, JDBCType jdbcType) {
        return new BoundParameter<>(value, null, Objects.requireNonNull(javaType, "javaType"), jdbcType);
    }

    /** Creates a provider parameter that fully bypasses default type routing. */
    public static <T> BoundParameter<T> bound(T value, ParameterBinder<? super T> binder) {
        if (binder == null) {
            throw new ConfigurationException("Bound parameter binder must not be null");
        }
        return new BoundParameter<>(value, binder, value == null ? null : value.getClass(), null);
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
}
