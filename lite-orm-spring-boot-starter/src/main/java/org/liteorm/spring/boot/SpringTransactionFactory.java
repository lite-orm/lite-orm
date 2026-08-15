package org.liteorm.spring.boot;

import org.liteorm.api.Transaction;
import org.liteorm.api.TransactionFactory;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Creates per-execution transaction handles backed by Spring DataSource synchronization.
 */
public final class SpringTransactionFactory implements TransactionFactory {

    private final DataSource dataSource;

    public SpringTransactionFactory(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public Transaction openTransaction() {
        return new SpringTransaction(dataSource);
    }
}
