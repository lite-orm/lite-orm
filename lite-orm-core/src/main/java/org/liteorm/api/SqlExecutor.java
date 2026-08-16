package org.liteorm.api;

/**
 * Executes immutable plans produced by generated Mapper implementations.
 */
public interface SqlExecutor {

    SqlResult execute(ExecutionPlan plan);

    default <T, R> R queryCursor(ExecutionPlan plan, CursorCallback<T, R> callback) {
        throw new UnsupportedOperationException("Cursor queries are not supported by this SqlExecutor");
    }
}
