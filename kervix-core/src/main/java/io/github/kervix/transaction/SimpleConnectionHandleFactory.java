package io.github.kervix.transaction;

import io.github.kervix.api.ConnectionHandle;
import io.github.kervix.api.ConnectionHandleFactory;
import io.github.kervix.api.TransactionDomain;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Objects;

/**
 * Opens auto-commit handles or joins the local transaction bound by the transactional executor.
 */
public final class SimpleConnectionHandleFactory implements ConnectionHandleFactory {

    private final DataSource dataSource;
    private final TransactionDomain domain;
    private final SimpleTransactionDomainGuard domainGuard;
    private final ThreadLocal<SimpleTransaction> currentTransaction = new ThreadLocal<>();
    private final ThreadLocal<SimpleTransactionDomainGuard.Binding> currentBinding = new ThreadLocal<>();

    public SimpleConnectionHandleFactory(DataSource dataSource) {
        this(dataSource, new TransactionDomain("default"), new SimpleTransactionDomainGuard());
    }

    public SimpleConnectionHandleFactory(
            DataSource dataSource,
            TransactionDomain domain,
            SimpleTransactionDomainGuard domainGuard) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.domain = Objects.requireNonNull(domain, "domain");
        this.domainGuard = Objects.requireNonNull(domainGuard, "domainGuard");
    }

    @Override
    public ConnectionHandle openHandle() {
        domainGuard.verify(domain);
        SimpleTransaction current = currentTransaction.get();
        return current == null ? new SimpleTransaction(dataSource, false) : new ParticipatingHandle(current);
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

    private record ParticipatingHandle(SimpleTransaction delegate) implements ConnectionHandle {

        @Override
        public Connection connection() {
            return delegate.connection();
        }

        @Override
        public void close() {
        }
    }
}
