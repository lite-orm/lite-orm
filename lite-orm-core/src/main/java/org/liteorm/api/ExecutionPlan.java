package org.liteorm.api;

import org.liteorm.jdbc.TypeHandlerManager;

import java.sql.JDBCType;
import java.util.Objects;

/**
 * Immutable input for one SQL execution.
 */
public class ExecutionPlan {

    private final String statementId;
    private final String sql;
    private final Object[] parameters;
    private final StatementType statementType;
    private final SqlSource sourceType;
    private final String generatedKeyColumn;
    private final ParameterBinder<?>[] parameterBinders;
    private final RowMapper<?> rowMapper;
    private final StatementOptions statementOptions;
    private final TypeRouting typeRouting;

    public ExecutionPlan(
            String statementId,
            String sql,
            Object[] parameters,
            StatementType statementType,
            SqlSource sourceType) {
        this(statementId, sql, parameters, statementType, sourceType, null, null, null);
    }

    public ExecutionPlan(
            String statementId,
            String sql,
            Object[] parameters,
            StatementType statementType,
            SqlSource sourceType,
            String generatedKeyColumn,
            ParameterBinder<?>[] parameterBinders,
            RowMapper<?> rowMapper) {
        this(statementId, sql, parameters, statementType, sourceType,
            generatedKeyColumn, parameterBinders, rowMapper, StatementOptions.defaults(), null);
    }

    public ExecutionPlan(
            String statementId,
            String sql,
            Object[] parameters,
            StatementType statementType,
            SqlSource sourceType,
            String generatedKeyColumn,
            ParameterBinder<?>[] parameterBinders,
            RowMapper<?> rowMapper,
            StatementOptions statementOptions) {
        this(statementId, sql, parameters, statementType, sourceType,
            generatedKeyColumn, parameterBinders, rowMapper, statementOptions, null);
    }

    public ExecutionPlan(
            String statementId,
            String sql,
            Object[] parameters,
            StatementType statementType,
            SqlSource sourceType,
            String generatedKeyColumn,
            ParameterBinder<?>[] parameterBinders,
            RowMapper<?> rowMapper,
            StatementOptions statementOptions,
            TypeRouting typeRouting) {
        this.statementId = Objects.requireNonNull(statementId, "statementId");
        this.sql = Objects.requireNonNull(sql, "sql");
        this.parameters = parameters == null ? new Object[0] : parameters.clone();
        this.statementType = Objects.requireNonNull(statementType, "statementType");
        this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
        if (generatedKeyColumn != null && generatedKeyColumn.isBlank()) {
            throw new IllegalArgumentException("generatedKeyColumn must not be blank");
        }
        this.generatedKeyColumn = generatedKeyColumn;
        this.parameterBinders = parameterBinders == null ? null : parameterBinders.clone();
        this.rowMapper = rowMapper;
        this.statementOptions = statementOptions == null ? StatementOptions.defaults() : statementOptions;
        this.typeRouting = typeRouting;
    }

    public String getStatementId() {
        return statementId;
    }

    public String getSql() {
        return sql;
    }

    public Object[] getParameters() {
        return parameters.clone();
    }

    public StatementType getStatementType() {
        return statementType;
    }

    public SqlSource getSourceType() {
        return sourceType;
    }

    public boolean returnsGeneratedKey() {
        return generatedKeyColumn != null;
    }

    public String getGeneratedKeyColumn() {
        return generatedKeyColumn;
    }

    public ParameterBinder<?>[] getParameterBinders() {
        return parameterBinders == null ? null : parameterBinders.clone();
    }

    public RowMapper<?> getRowMapper() {
        return rowMapper;
    }

    public StatementOptions getStatementOptions() {
        return statementOptions;
    }

    /** Returns generated routing metadata, or {@code null} when the plan uses default JDBC access. */
    public TypeRouting getTypeRouting() {
        return typeRouting;
    }

    /**
     * Compile-time type information used to route JDBC values at execution time.
     *
     * <p>The value is immutable and thread-safe when its manager is thread-safe. Parameter JDBC
     * types may be null as a group or contain null entries for inferred representations. Result
     * labels may be null only for a single scalar target.</p>
     */
    public static final class TypeRouting {

        private final TypeHandlerManager manager;
        private final Class<?>[] parameterTypes;
        private final JDBCType[] parameterJdbcTypes;
        private final Class<?>[] resultTypes;
        private final String[] resultColumnLabels;

        public TypeRouting(
                TypeHandlerManager manager,
                Class<?>[] parameterTypes,
                JDBCType[] parameterJdbcTypes,
                Class<?>[] resultTypes,
                String[] resultColumnLabels) {
            this.manager = Objects.requireNonNull(manager, "manager");
            this.parameterTypes = copy(parameterTypes);
            this.parameterJdbcTypes = parameterJdbcTypes == null ? null : parameterJdbcTypes.clone();
            this.resultTypes = copy(resultTypes);
            this.resultColumnLabels = resultColumnLabels == null ? null : resultColumnLabels.clone();
            if (this.parameterJdbcTypes != null
                    && this.parameterJdbcTypes.length != this.parameterTypes.length) {
                throw new IllegalArgumentException("parameter JDBC types must align with parameter types");
            }
            if (this.resultColumnLabels != null
                    && this.resultColumnLabels.length != this.resultTypes.length) {
                throw new IllegalArgumentException("result labels must align with result types");
            }
            if (this.resultColumnLabels == null && this.resultTypes.length > 1) {
                throw new IllegalArgumentException("multiple result types require aligned result labels");
            }
        }

        /** Returns the type-handler manager used by this execution plan. */
        public TypeHandlerManager manager() {
            return manager;
        }

        /** Returns a defensive copy of parameter Java types in placeholder order. */
        public Class<?>[] parameterTypes() {
            return parameterTypes.clone();
        }

        /** Returns aligned JDBC types, or {@code null} when every representation is inferred. */
        public JDBCType[] parameterJdbcTypes() {
            return parameterJdbcTypes == null ? null : parameterJdbcTypes.clone();
        }

        /** Returns a defensive copy of result Java target types. */
        public Class<?>[] resultTypes() {
            return resultTypes.clone();
        }

        /** Returns aligned result labels, or {@code null} for a scalar result. */
        public String[] resultColumnLabels() {
            return resultColumnLabels == null ? null : resultColumnLabels.clone();
        }

        private static Class<?>[] copy(Class<?>[] types) {
            return types == null ? new Class<?>[0] : types.clone();
        }
    }

    public enum StatementType {
        SELECT,
        INSERT,
        UPDATE,
        DELETE,
        BATCH
    }

    public enum SqlSource {
        XML,
        ANNOTATION,
        SCRIPT,
        GENERATED
    }
}
