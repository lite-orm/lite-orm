package org.liteorm.api;

import java.sql.JDBCType;
import java.util.Objects;

/**
 * Immutable input for one SQL execution.
 */
public class ExecutionPlan {

    private final String sql;
    private final Object[] parameters;
    private final Definition definition;

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
        this(new Definition(statementId, statementType, sourceType, generatedKeyColumn,
            parameterBinders, rowMapper, statementOptions, typeRouting), sql, parameters);
    }

    ExecutionPlan(Definition definition, String sql, Object[] parameters) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.sql = Objects.requireNonNull(sql, "sql");
        this.parameters = parameters == null ? new Object[0] : parameters.clone();
    }

    public String getStatementId() {
        return definition.statementId;
    }

    public String getSql() {
        return sql;
    }

    public Object[] getParameters() {
        return parameters.clone();
    }

    public StatementType getStatementType() {
        return definition.statementType;
    }

    public SqlSource getSourceType() {
        return definition.sourceType;
    }

    public boolean returnsGeneratedKey() {
        return definition.generatedKeyColumn != null;
    }

    public String getGeneratedKeyColumn() {
        return definition.generatedKeyColumn;
    }

    public ParameterBinder<?>[] getParameterBinders() {
        return definition.parameterBinders == null ? null : definition.parameterBinders.clone();
    }

    public RowMapper<?> getRowMapper() {
        return definition.rowMapper;
    }

    public StatementOptions getStatementOptions() {
        return definition.statementOptions;
    }

    /** Returns generated routing metadata, or {@code null} when the plan uses default JDBC access. */
    public TypeRouting getTypeRouting() {
        return definition.typeRouting;
    }

    static final class Definition {
        private final String statementId;
        private final StatementType statementType;
        private final SqlSource sourceType;
        private final String generatedKeyColumn;
        private final ParameterBinder<?>[] parameterBinders;
        private final RowMapper<?> rowMapper;
        private final StatementOptions statementOptions;
        private final TypeRouting typeRouting;

        Definition(
                String statementId, StatementType statementType, SqlSource sourceType,
                String generatedKeyColumn, ParameterBinder<?>[] parameterBinders,
                RowMapper<?> rowMapper, StatementOptions statementOptions, TypeRouting typeRouting) {
            this.statementId = Objects.requireNonNull(statementId, "statementId");
            this.statementType = Objects.requireNonNull(statementType, "statementType");
            this.sourceType = Objects.requireNonNull(sourceType, "sourceType");
            if (generatedKeyColumn != null && generatedKeyColumn.isBlank()) {
                throw new IllegalArgumentException("generatedKeyColumn must not be blank");
            }
            this.generatedKeyColumn = generatedKeyColumn;
            this.parameterBinders = parameterBinders == null ? null : parameterBinders.clone();
            this.rowMapper = rowMapper;
            this.statementOptions = statementOptions == null
                ? StatementOptions.defaults() : statementOptions;
            this.typeRouting = typeRouting;
        }
    }

    /**
     * Compile-time type information used to route JDBC values at execution time.
     *
     * <p>The value is immutable and thread-safe. A null parameter Java type preserves direct JDBC
     * binding when a dynamic expression has no statically known type. Parameter JDBC types may be
     * null as a group or contain null entries for inferred representations. Result labels may be
     * null only for a single scalar target.</p>
     */
    public static final class TypeRouting {

        private final Class<?>[] parameterTypes;
        private final JDBCType[] parameterJdbcTypes;
        private final Class<?>[] resultTypes;
        private final String[] resultColumnLabels;

        public TypeRouting(
                Class<?>[] parameterTypes,
                JDBCType[] parameterJdbcTypes,
                Class<?>[] resultTypes,
                String[] resultColumnLabels) {
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

        static TypeRouting bindParameters(TypeRouting resultRouting, BoundSql boundSql) {
            Class<?>[] resultTypes = resultRouting == null
                ? new Class<?>[0] : resultRouting.resultTypes();
            String[] resultLabels = resultRouting == null
                ? null : resultRouting.resultColumnLabels();
            return new TypeRouting(
                boundSql.parameterTypes(), boundSql.parameterJdbcTypes(), resultTypes, resultLabels);
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
