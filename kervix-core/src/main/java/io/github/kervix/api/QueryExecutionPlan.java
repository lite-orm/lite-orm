package io.github.kervix.api;

/**
 * Immutable execution plan for a query whose rows are mapped to one Java result type.
 *
 * @param <T> mapped query result type
 */
public final class QueryExecutionPlan<T> extends ExecutionPlan {

    private final ResultAssembler<T> resultAssembler;

    QueryExecutionPlan(QueryDefinition<T> definition, Object[] parameters) {
        super(definition.planDefinition(), definition.sql(), parameters);
        this.resultAssembler = definition.resultAssembler();
    }

    QueryExecutionPlan(QueryDefinition<T> definition, BoundSql boundSql) {
        super(new ExecutionPlan.Definition(
            definition.statementId(), StatementType.SELECT, definition.sourceType(), null,
            boundSql.parameterBinders(), definition.rowMapper(), definition.statementOptions(),
            definition.routingFor(boundSql)), boundSql.sql(), boundSql.parameterValues());
        this.resultAssembler = definition.resultAssembler();
    }

    /**
     * Supports direct plan construction for custom executor integrations.
     * Exactly one of {@code rowMapper} and {@code resultAssembler} must be supplied.
     */
    public QueryExecutionPlan(
            String statementId,
            String sql,
            Object[] parameters,
            StatementType statementType,
            SqlSource sourceType,
            String generatedKeyColumn,
            ParameterBinder<?>[] parameterBinders,
            RowMapper<T> rowMapper,
            ResultAssembler<T> resultAssembler,
            StatementOptions statementOptions,
            TypeRouting typeRouting) {
        super(statementId, sql, parameters, statementType, sourceType,
            generatedKeyColumn, parameterBinders, rowMapper, statementOptions, typeRouting);
        if (statementType != StatementType.SELECT) {
            throw new IllegalArgumentException("query plan requires SELECT statement type");
        }
        if (generatedKeyColumn != null) {
            throw new IllegalArgumentException("query plan cannot return a generated key");
        }
        if (rowMapper == null && resultAssembler == null) {
            throw new IllegalArgumentException("query plan requires a row mapper or result assembler");
        }
        if (rowMapper != null && resultAssembler != null) {
            throw new IllegalArgumentException("query plan cannot combine a row mapper and result assembler");
        }
        this.resultAssembler = resultAssembler;
    }

    /** Returns the generated assembler, or {@code null} when a custom row mapper owns mapping. */
    public ResultAssembler<T> getResultAssembler() {
        return resultAssembler;
    }
}
