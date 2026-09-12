package org.liteorm.api;

import java.sql.JDBCType;
import java.util.ArrayList;
import java.util.List;

public record BoundSql(String sql, List<BoundParameter<?>> parameters) {

    private static final ParameterBinder<Object> DIRECT_BINDER =
        (statement, index, value) -> statement.setObject(index, value);

    public BoundSql {
        if (sql == null || sql.isBlank()) {
            throw new ConfigurationException("SQL provider returned blank SQL");
        }
        if (parameters == null) {
            throw new ConfigurationException("SQL provider returned null parameter list");
        }
        parameters = List.copyOf(parameters);
    }

    /**
     * Creates bound SQL from aligned arrays retained for public compatibility.
     *
     * <p>Generated Mappers use {@link BoundSqlBuilder}; providers may continue constructing
     * {@code BoundSql} directly. Null values represent an empty value array or absent optional
     * metadata arrays.</p>
     */
    public static BoundSql of(
            String sql,
            Object[] values,
            ParameterBinder<?>[] binders,
            Class<?>[] javaTypes,
            JDBCType[] jdbcTypes) {
        Object[] safeValues = values == null ? new Object[0] : values.clone();
        Class<?>[] safeJavaTypes = javaTypes == null ? new Class<?>[safeValues.length] : javaTypes.clone();
        if (safeJavaTypes.length != safeValues.length
                || binders != null && binders.length != safeValues.length
                || jdbcTypes != null && jdbcTypes.length != safeValues.length) {
            throw new ConfigurationException("Generated SQL parameter metadata must be aligned");
        }
        List<BoundParameter<?>> parameters = new ArrayList<>(safeValues.length);
        for (int index = 0; index < safeValues.length; index++) {
            parameters.add(generatedParameter(
                safeValues[index],
                binders == null ? null : binders[index],
                safeJavaTypes[index],
                jdbcTypes == null ? null : jdbcTypes[index]));
        }
        return new BoundSql(sql, parameters);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    static BoundParameter<?> generatedParameter(
            Object value, ParameterBinder<?> binder, Class<?> javaType, JDBCType jdbcType) {
        ParameterBinder effectiveBinder = binder;
        if (effectiveBinder == null && javaType == null) {
            effectiveBinder = DIRECT_BINDER;
        }
        return new BoundParameter(value, effectiveBinder, javaType, jdbcType);
    }

    public static BoundSql requireValid(BoundSql boundSql, String statementId) {
        if (boundSql == null) {
            throw ConfigurationException.forStatement("SQL provider returned null BoundSql", statementId);
        }
        return boundSql;
    }

    /** Returns parameter values in placeholder order. */
    public Object[] parameterValues() {
        return parameters.stream().map(BoundParameter::value).toArray(Object[]::new);
    }

    /** Returns explicit binder slots in placeholder order. */
    public ParameterBinder<?>[] parameterBinders() {
        return parameters.stream().map(BoundParameter::binder).toArray(ParameterBinder<?>[]::new);
    }

    /** Returns declared Java types used by default runtime routing. */
    public Class<?>[] parameterTypes() {
        return parameters.stream().map(BoundParameter::javaType).toArray(Class<?>[]::new);
    }

    /** Returns optional JDBC representations used by default runtime routing. */
    public JDBCType[] parameterJdbcTypes() {
        return parameters.stream().map(BoundParameter::jdbcType).toArray(JDBCType[]::new);
    }
}
