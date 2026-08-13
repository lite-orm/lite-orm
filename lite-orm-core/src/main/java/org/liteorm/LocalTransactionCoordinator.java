package org.liteorm;

import org.liteorm.api.ConnectionProvider;
import org.liteorm.api.TransactionContext;
import org.liteorm.api.TransactionCoordinator;
import org.liteorm.api.TransactionException;
import org.liteorm.api.TransactionOperations;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/**
 * Thread-confined transaction coordinator for standalone JDBC usage.
 */
public final class LocalTransactionCoordinator implements TransactionCoordinator, TransactionOperations {

    private final ConnectionProvider connectionProvider;
    private final ThreadLocal<TransactionContext> currentTransaction = new ThreadLocal<>();

    public LocalTransactionCoordinator(ConnectionProvider connectionProvider) {
        this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
    }

    @Override
    public TransactionContext begin() throws TransactionException {
        if (isActive()) {
            throw new TransactionException(
                TransactionException.Type.BEGIN_FAILED,
                "Already in transaction: " + currentTransaction.get().getTransactionId()
            );
        }

        Connection connection = connectionProvider.acquire();
        try {
            connection.setAutoCommit(false);
            TransactionContext context = new TransactionContext(connection, "tx-" + UUID.randomUUID().toString().substring(0, 8));
            currentTransaction.set(context);
            return context;
        } catch (SQLException failure) {
            releaseAfterBeginFailure(connection, failure);
            throw new TransactionException(
                TransactionException.Type.BEGIN_FAILED,
                "Failed to begin transaction: " + failure.getMessage(),
                failure
            );
        }
    }

    @Override
    public void commit(TransactionContext context) throws TransactionException {
        validate(context);
        Throwable failure = null;
        try {
            context.getConnection().commit();
            context.setCommitted(true);
        } catch (SQLException commitFailure) {
            failure = new TransactionException(
                TransactionException.Type.COMMIT_FAILED,
                "Transaction commit failed: " + commitFailure.getMessage(),
                commitFailure
            );
            try {
                context.getConnection().rollback();
                context.setRolledBack(true);
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
        }

        failure = restoreAndRelease(context.getConnection(), failure);
        throwIfFailed(failure);
    }

    @Override
    public void rollback(TransactionContext context) throws TransactionException {
        validate(context);
        Throwable failure = null;
        try {
            context.getConnection().rollback();
            context.setRolledBack(true);
        } catch (SQLException rollbackFailure) {
            failure = new TransactionException(
                TransactionException.Type.ROLLBACK_FAILED,
                "Transaction rollback failed: " + rollbackFailure.getMessage(),
                rollbackFailure
            );
        }

        failure = restoreAndRelease(context.getConnection(), failure);
        throwIfFailed(failure);
    }

    @Override
    public Connection currentConnection() {
        TransactionContext context = currentTransaction.get();
        return context == null || !context.isActive() ? null : context.getConnection();
    }

    private void validate(TransactionContext context) throws TransactionException {
        TransactionContext current = currentTransaction.get();
        if (context == null || !context.isActive() || current != context) {
            throw new TransactionException(TransactionException.Type.BEGIN_FAILED, "Transaction context mismatch");
        }
    }

    private Throwable restoreAndRelease(Connection connection, Throwable failure) {
        try {
            connection.setAutoCommit(true);
        } catch (SQLException restoreFailure) {
            failure = appendCleanupFailure(failure, restoreFailure, "Failed to restore auto-commit");
        } finally {
            currentTransaction.remove();
        }

        try {
            connectionProvider.release(connection);
        } catch (RuntimeException releaseFailure) {
            failure = appendCleanupFailure(failure, releaseFailure, "Failed to release transaction connection");
        }
        return failure;
    }

    private void releaseAfterBeginFailure(Connection connection, SQLException failure) {
        try {
            connectionProvider.release(connection);
        } catch (RuntimeException releaseFailure) {
            failure.addSuppressed(releaseFailure);
        }
    }

    private Throwable appendCleanupFailure(Throwable primary, Throwable secondary, String message) {
        if (primary == null) {
            return new TransactionException(
                TransactionException.Type.CLEANUP_FAILED,
                message + ": " + secondary.getMessage(),
                secondary
            );
        }
        primary.addSuppressed(secondary);
        return primary;
    }

    private void throwIfFailed(Throwable failure) throws TransactionException {
        if (failure == null) {
            return;
        }
        if (failure instanceof TransactionException transactionFailure) {
            throw transactionFailure;
        }
        throw new TransactionException(TransactionException.Type.CLEANUP_FAILED, failure.getMessage(), failure);
    }
}
