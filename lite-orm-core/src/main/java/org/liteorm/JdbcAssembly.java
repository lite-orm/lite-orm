package org.liteorm;

import org.liteorm.api.ConfigurationException;
import org.liteorm.api.ExecutionInterceptor;
import org.liteorm.api.SqlExecutor;
import org.liteorm.api.TransactionDomain;
import org.liteorm.api.TransactionalExecutor;
import org.liteorm.jdbc.JdbcSqlExecutor;
import org.liteorm.transaction.SimpleTransactionDomainGuard;
import org.liteorm.transaction.SimpleTransactionFactory;
import org.liteorm.transaction.SimpleTransactionalExecutor;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable set of JDBC execution and transaction roles assembled for one DataSource domain.
 */
public final class JdbcAssembly {

    private final SqlExecutor sqlExecutor;
    private final TransactionalExecutor transactionalExecutor;

    private JdbcAssembly(SqlExecutor sqlExecutor, TransactionalExecutor transactionalExecutor) {
        this.sqlExecutor = sqlExecutor;
        this.transactionalExecutor = transactionalExecutor;
    }

    public SqlExecutor sqlExecutor() {
        return sqlExecutor;
    }

    public TransactionalExecutor transactionalExecutor() {
        return transactionalExecutor;
    }

    public static final class Builder {

        private final DataSource dataSource;
        private TransactionDomain domain = new TransactionDomain("default");
        private SimpleTransactionDomainGuard domainGuard = new SimpleTransactionDomainGuard();
        private List<ExecutionInterceptor> interceptors = List.of();

        Builder(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        public Builder domain(String key) {
            try {
                domain = new TransactionDomain(key);
            } catch (IllegalArgumentException failure) {
                throw new ConfigurationException("domain must not be blank", failure);
            }
            return this;
        }

        public Builder domainGuard(SimpleTransactionDomainGuard domainGuard) {
            if (domainGuard == null) {
                throw new ConfigurationException("domainGuard must not be null");
            }
            this.domainGuard = domainGuard;
            return this;
        }

        public Builder interceptors(List<ExecutionInterceptor> interceptors) {
            if (interceptors == null) {
                throw new ConfigurationException("interceptors must not be null");
            }

            List<ExecutionInterceptor> copy = new ArrayList<>(interceptors.size());
            Map<ExecutionInterceptor, Boolean> identities = new IdentityHashMap<>();
            for (int index = 0; index < interceptors.size(); index++) {
                ExecutionInterceptor interceptor = interceptors.get(index);
                if (interceptor == null) {
                    throw new ConfigurationException("interceptors[" + index + "] must not be null");
                }
                if (identities.put(interceptor, Boolean.TRUE) != null) {
                    throw new ConfigurationException("duplicate interceptor instance at index " + index);
                }
                copy.add(interceptor);
            }
            this.interceptors = List.copyOf(copy);
            return this;
        }

        public JdbcAssembly build() {
            SimpleTransactionFactory transactionFactory = new SimpleTransactionFactory(
                dataSource, domain, domainGuard);
            return new JdbcAssembly(
                new JdbcSqlExecutor(transactionFactory, interceptors),
                new SimpleTransactionalExecutor(transactionFactory)
            );
        }
    }
}
