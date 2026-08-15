package org.liteorm.transaction;

import org.liteorm.api.TransactionCallback;
import org.liteorm.api.TransactionalExecutor;

import java.util.Objects;

/**
 * Executes callbacks inside a thread-bound SimpleTransaction.
 */
public final class SimpleTransactionalExecutor implements TransactionalExecutor {

    private final SimpleTransactionFactory transactionFactory;

    public SimpleTransactionalExecutor(SimpleTransactionFactory transactionFactory) {
        this.transactionFactory = Objects.requireNonNull(transactionFactory, "transactionFactory");
    }

    @Override
    public <T> T execute(TransactionCallback<T> callback) {
        Objects.requireNonNull(callback, "callback");
        SimpleTransaction current = transactionFactory.currentTransaction();
        if (current != null) {
            return callback.execute(transactionFactory.openTransaction());
        }

        SimpleTransaction transaction = transactionFactory.beginTransaction();
        Throwable primaryFailure = null;
        try {
            T result = callback.execute(transaction);
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
                transactionFactory.clear(transaction);
            }
        }
    }
}
