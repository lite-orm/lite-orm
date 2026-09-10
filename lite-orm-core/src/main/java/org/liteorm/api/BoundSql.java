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

    /** Creates bound SQL from aligned arrays emitted by generated dynamic SQL code. */
    @SuppressWarnings({"rawtypes", "unchecked"})
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
            ParameterBinder binder = binders == null ? null : binders[index];
            Class<?> javaType = safeJavaTypes[index];
            JDBCType jdbcType = jdbcTypes == null ? null : jdbcTypes[index];
            if (binder == null && javaType == null) {
                binder = DIRECT_BINDER;
            }
            parameters.add(new BoundParameter(safeValues[index], binder, javaType, jdbcType));
        }
        return new BoundSql(sql, parameters);
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
