package org.liteorm.api;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes immutable plans produced by generated Mapper implementations.
 */
public interface SqlExecutor {

    SqlResult<?> execute(ExecutionPlan plan);

    /** Executes a typed query through the existing execution lifecycle. */
    default <T> QueryResult<T> query(QueryExecutionPlan<T> plan) {
        return QueryResult.from(execute(plan), plan.getStatementId());
    }

    /** Executes an insert, update, or delete through the existing execution lifecycle. */
    default UpdateResult update(ExecutionPlan plan) {
        return UpdateResult.from(execute(plan), plan.getStatementId());
    }

    /** Executes an insert and returns its generated key through the existing lifecycle. */
    default <K> GeneratedKeyResult<K> generatedKey(ExecutionPlan plan) {
        return GeneratedKeyResult.from(execute(plan), plan.getStatementId());
    }

    /** Executes a JDBC batch through the existing execution lifecycle. */
    default BatchResult batch(BatchExecutionPlan plan) {
        return BatchResult.from(execute(plan), plan.getStatementId());
    }

    /** Executes a typed query plan and returns its assembled Java results. */
    @SuppressWarnings("unchecked")
    default <T> SqlResult<T> execute(QueryExecutionPlan<T> plan) {
        SqlResult<?> result = execute((ExecutionPlan) plan);
        ResultAssembler<T> assembler = plan.getResultAssembler();
        List<?> rows = result.getQueryResults();
        if (assembler == null || rows == null || rows.isEmpty() || !(rows.getFirst() instanceof Object[])) {
            return (SqlResult<T>) result;
        }

        int[] columnIndexes = resultColumnIndexes(plan, result);
        List<T> mappedRows = new ArrayList<>(rows.size());
        for (Object row : rows) {
            mappedRows.add(assembler.assemble(new ResultRow((Object[]) row, columnIndexes)));
        }
        return SqlResult.forMappedQuery(result.getResultColumns(), mappedRows);
    }

    private int[] resultColumnIndexes(QueryExecutionPlan<?> plan, SqlResult<?> result) {
        ExecutionPlan.TypeRouting typeRouting = plan.getTypeRouting();
        String[] labels = typeRouting == null ? null : typeRouting.resultColumnLabels();
        int count = labels == null
            ? typeRouting == null ? result.getResultColumns().size() : typeRouting.resultTypes().length
            : labels.length;
        int[] indexes = new int[count];
        for (int index = 0; index < count; index++) {
            indexes[index] = labels == null ? index : result.requireColumnIndex(labels[index]);
        }
        return indexes;
    }

    default <T, R> R queryCursor(ExecutionPlan plan, CursorCallback<T, R> callback) {
        throw new UnsupportedOperationException("Cursor queries are not supported by this SqlExecutor");
    }
}
