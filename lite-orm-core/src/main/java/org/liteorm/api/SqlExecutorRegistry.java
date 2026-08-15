package org.liteorm.api;

/**
 * Read-only lookup of explicitly assembled named SqlExecutor instances.
 */
@FunctionalInterface
public interface SqlExecutorRegistry {

    SqlExecutor require(String statementId, String dataSourceKey);
}
