package org.liteorm.api;

/**
 * Executes application work inside an explicit transaction boundary.
 */
public interface TransactionalExecutor {

    <T> T execute(TransactionCallback<T> callback);
}
