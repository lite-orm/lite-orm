package org.liteorm.api;

/**
 * Executes immutable plans produced by generated Mapper implementations.
 */
public interface SqlExecutor {

    SqlResult execute(ExecutionPlan plan);
}
