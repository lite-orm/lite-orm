package org.liteorm.transaction;

import org.liteorm.api.TransactionCallback;
import org.liteorm.api.TransactionException;
import org.liteorm.api.TransactionalExecutor;

import java.util.Objects;

/**
 * Executes callbacks inside a thread-bound SimpleTransaction.
 */
public final class SimpleTransactionalExecutor implements TransactionalExecutor {

    private final SimpleConnectionHandleFactory connectionHandleFactory;

    public SimpleTransactionalExecutor(SimpleConnectionHandleFactory connectionHandleFactory) {
        this.connectionHandleFactory = Objects.requireNonNull(connectionHandleFactory, "connectionHandleFactory");
    }

    @Override
    public <T> T execute(TransactionCallback<T> callback) {
        Objects.requireNonNull(callback, "callback");
        SimpleTransaction current = connectionHandleFactory.currentTransaction();
        if (current != null) {
            try {
                return callback.execute();
            } catch (RuntimeException | Error failure) {
                current.markRollbackOnly();
                throw failure;
            }
        }

        SimpleTransaction transaction = connectionHandleFactory.beginTransaction();
        Throwable primaryFailure = null;
        try {
            T result = callback.execute();
            if (transaction.isRollbackOnly()) {
                transaction.rollback();
                throw new TransactionException(
                    TransactionException.Type.ROLLBACK_ONLY,
                    "Nested transaction work failed; the root transaction was rolled back"
                );
            }
            transaction.commit();
            return result;
        } catch (RuntimeException | Error failure) {
            primaryFailure = failure;
            if (transaction.isRollbackRequired()) {
                try {
                    transaction.rollback();
                } catch (RuntimeException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            throw failure;
        } finally {
            try {
                transaction.close();
            } catch (RuntimeException closeFailure) {
                if (primaryFailure != null) {
                    primaryFailure.addSuppressed(closeFailure);
                } else {
                    throw closeFailure;
                }
            } finally {
                connectionHandleFactory.clear(transaction);
            }
        }
    }
}
