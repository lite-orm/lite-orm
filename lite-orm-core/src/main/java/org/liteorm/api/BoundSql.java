package org.liteorm.api;

import java.util.List;

public record BoundSql(String sql, List<BoundParameter<?>> parameters) {

    public BoundSql {
        if (sql == null || sql.isBlank()) {
            throw new ConfigurationException("SQL provider returned blank SQL");
        }
        if (parameters == null) {
            throw new ConfigurationException("SQL provider returned null parameter list");
        }
        parameters = List.copyOf(parameters);
    }

    public static BoundSql requireValid(BoundSql boundSql, String statementId) {
        if (boundSql == null) {
            throw ConfigurationException.forStatement("SQL provider returned null BoundSql", statementId);
        }
        return boundSql;
    }

    public Object[] parameterValues() {
        return parameters.stream().map(BoundParameter::value).toArray(Object[]::new);
    }

    public ParameterBinder<?>[] parameterBinders() {
        return parameters.stream().map(BoundParameter::binder).toArray(ParameterBinder<?>[]::new);
    }
}
