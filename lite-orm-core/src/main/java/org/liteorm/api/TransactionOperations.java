package org.liteorm.api;

/**
 * Manual transaction boundary operations for standalone LiteORM usage.
 */
public interface TransactionOperations {

    TransactionContext begin() throws TransactionException;

    void commit(TransactionContext context) throws TransactionException;

    void rollback(TransactionContext context) throws TransactionException;
}
