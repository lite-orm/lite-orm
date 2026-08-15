package org.liteorm.transaction;

import org.liteorm.api.Transaction;
import org.liteorm.api.TransactionDomain;
import org.liteorm.api.TransactionException;
import org.liteorm.api.TransactionFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Objects;

/**
 * Creates temporary transactions or joins the transaction bound by SimpleTransactionalExecutor.
 */
public final class SimpleTransactionFactory implements TransactionFactory {

    private final DataSource dataSource;
    private final TransactionDomain domain;
    private final SimpleTransactionDomainGuard domainGuard;
    private final ThreadLocal<SimpleTransaction> currentTransaction = new ThreadLocal<>();
    private final ThreadLocal<SimpleTransactionDomainGuard.Binding> currentBinding = new ThreadLocal<>();

    public SimpleTransactionFactory(DataSource dataSource) {
        this(dataSource, new TransactionDomain("default"), new SimpleTransactionDomainGuard());
    }

    public SimpleTransactionFactory(
            DataSource dataSource,
            TransactionDomain domain,
            SimpleTransactionDomainGuard domainGuard) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.domain = Objects.requireNonNull(domain, "domain");
        this.domainGuard = Objects.requireNonNull(domainGuard, "domainGuard");
    }

    @Override
    public Transaction openTransaction() {
        domainGuard.verify(domain);
        SimpleTransaction current = currentTransaction.get();
        return current == null ? new SimpleTransaction(dataSource, false) : new ParticipatingTransaction(current);
    }

    SimpleTransaction currentTransaction() {
        return currentTransaction.get();
    }

    SimpleTransaction beginTransaction() {
        SimpleTransactionDomainGuard.Binding binding = domainGuard.bind(domain);
        SimpleTransaction transaction = new SimpleTransaction(dataSource, true);
        currentBinding.set(binding);
        currentTransaction.set(transaction);
        return transaction;
    }

    void clear(SimpleTransaction transaction) {
        if (currentTransaction.get() == transaction) {
            currentTransaction.remove();
            SimpleTransactionDomainGuard.Binding binding = currentBinding.get();
            currentBinding.remove();
            domainGuard.clear(binding);
        }
    }

    private record ParticipatingTransaction(SimpleTransaction delegate) implements Transaction {

        @Override
        public Connection getConnection() {
            return delegate.getConnection();
        }

        @Override
        public void commit() {
            throw completionFailure();
        }

        @Override
        public void rollback() {
            throw completionFailure();
        }

        @Override
        public void close() {
        }

        @Override
        public Integer getTimeoutSeconds() {
            return delegate.getTimeoutSeconds();
        }

        private TransactionException completionFailure() {
            return new TransactionException(
                TransactionException.Type.CLEANUP_FAILED,
                "A participating transaction handle cannot complete the root transaction"
            );
        }
    }
}
