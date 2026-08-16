package org.liteorm.spring.boot;

import org.liteorm.api.Transaction;
import org.liteorm.api.TransactionException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Objects;

/**
 * Transaction handle that participates in Spring's thread-bound DataSource lifecycle.
 */
public final class SpringTransaction implements Transaction {

    private final DataSource dataSource;
    private Connection connection;
    private boolean closed;

    SpringTransaction(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Connection getConnection() {
        if (closed) {
            throw new TransactionException(
                TransactionException.Type.CLEANUP_FAILED,
                "Spring transaction handle is closed"
            );
        }
        if (connection == null) {
            verifyTransactionDataSource();
            connection = DataSourceUtils.getConnection(dataSource);
        }
        return connection;
    }

    @Override
    public void commit() {
    }

    @Override
    public void rollback() {
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (connection != null) {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private void verifyTransactionDataSource() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.hasResource(dataSource)) {
            throw new TransactionException(
                TransactionException.Type.DOMAIN_MISMATCH,
                "Active Spring transaction is not bound to the configured LiteORM DataSource"
            );
        }
    }
}
