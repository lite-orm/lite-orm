package io.github.lynxus.transaction;

import io.github.lynxus.api.ConnectionHandle;
import io.github.lynxus.api.TransactionException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Minimal JDBC transaction backed by one DataSource connection.
 */
final class SimpleTransaction implements ConnectionHandle {

    private final DataSource dataSource;
    private final boolean transactional;
    private Connection connection;
    private boolean originalAutoCommit;
    private boolean transactionStarted;
    private boolean rollbackOnly;
    private boolean completed;
    private boolean closed;

    SimpleTransaction(DataSource dataSource, boolean transactional) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.transactional = transactional;
    }

    @Override
    public Connection connection() {
        ensureOpen();
        if (connection == null) {
            try {
                connection = dataSource.getConnection();
                originalAutoCommit = connection.getAutoCommit();
                if (transactional && originalAutoCommit) {
                    connection.setAutoCommit(false);
                }
                transactionStarted = transactional;
            } catch (SQLException failure) {
                throw new TransactionException(
                    TransactionException.Type.BEGIN_FAILED,
                    "Failed to acquire transaction connection: " + failure.getMessage(),
                    failure
                );
            }
        }
        return connection;
    }

    void commit() {
        ensureCompletable();
        if (!transactional || connection == null) {
            completed = true;
            return;
        }
        try {
            connection.commit();
            completed = true;
        } catch (SQLException commitFailure) {
            TransactionException failure = new TransactionException(
                TransactionException.Type.COMMIT_FAILED,
                "Transaction commit failed: " + commitFailure.getMessage(),
                commitFailure
            );
            try {
                connection.rollback();
            } catch (SQLException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            }
            completed = true;
            throw failure;
        }
    }

    void rollback() {
        ensureCompletable();
        if (!transactional || connection == null) {
            completed = true;
            return;
        }
        try {
            connection.rollback();
            completed = true;
        } catch (SQLException failure) {
            completed = true;
            throw new TransactionException(
                TransactionException.Type.ROLLBACK_FAILED,
                "Transaction rollback failed: " + failure.getMessage(),
                failure
            );
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (connection == null) {
            return;
        }

        TransactionException failure = null;
        if (transactional) {
            try {
                if (connection.getAutoCommit() != originalAutoCommit) {
                    connection.setAutoCommit(originalAutoCommit);
                }
            } catch (SQLException restoreFailure) {
                failure = cleanupFailure("Failed to restore connection auto-commit", restoreFailure);
            }
        }
        try {
            connection.close();
        } catch (SQLException closeFailure) {
            if (failure == null) {
                failure = cleanupFailure("Failed to close transaction connection", closeFailure);
            } else {
                failure.addSuppressed(closeFailure);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    boolean isRollbackRequired() {
        return transactionStarted && !completed;
    }

    void markRollbackOnly() {
        ensureOpen();
        rollbackOnly = true;
    }

    boolean isRollbackOnly() {
        return rollbackOnly;
    }

    private void ensureOpen() {
        if (closed) {
            throw new TransactionException(TransactionException.Type.CLEANUP_FAILED, "Transaction is closed");
        }
    }

    private void ensureCompletable() {
        ensureOpen();
        if (completed) {
            throw new TransactionException(TransactionException.Type.CLEANUP_FAILED, "Transaction is already completed");
        }
    }

    private TransactionException cleanupFailure(String message, SQLException cause) {
        return new TransactionException(
            TransactionException.Type.CLEANUP_FAILED,
            message + ": " + cause.getMessage(),
            cause
        );
    }
}
