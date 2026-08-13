package org.liteorm;

import org.liteorm.api.ConnectionProvider;
import org.liteorm.api.ExecutionInterceptor;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.SqlEngine;
import org.liteorm.api.SqlResult;
import org.liteorm.api.TransactionContext;
import org.liteorm.api.TransactionException;
import org.liteorm.api.TransactionOperations;
import org.liteorm.runtime.ExecutionProcessor;
import org.liteorm.runtime.ParameterProcessor;
import org.liteorm.runtime.ResultProcessor;
import org.liteorm.runtime.SqlProcessor;

import java.util.List;

/**
 * Standalone SQL engine with LiteORM-managed local transaction boundaries.
 */
public final class StandaloneSqlEngine implements SqlEngine, TransactionOperations {

    private final DefaultSqlEngine sqlEngine;
    private final LocalTransactionCoordinator transactionCoordinator;

    public StandaloneSqlEngine(ConnectionProvider connectionProvider) {
        this(connectionProvider, defaultProcessors(), List.of());
    }

    public StandaloneSqlEngine(
            ConnectionProvider connectionProvider,
            List<SqlProcessor> processors,
            List<ExecutionInterceptor> interceptors) {
        this.transactionCoordinator = new LocalTransactionCoordinator(connectionProvider);
        this.sqlEngine = new DefaultSqlEngine(
            connectionProvider,
            transactionCoordinator,
            processors,
            interceptors
        );
    }

    @Override
    public SqlResult execute(ExecutionPlan plan) {
        return sqlEngine.execute(plan);
    }

    @Override
    public TransactionContext begin() throws TransactionException {
        return transactionCoordinator.begin();
    }

    @Override
    public void commit(TransactionContext context) throws TransactionException {
        transactionCoordinator.commit(context);
    }

    @Override
    public void rollback(TransactionContext context) throws TransactionException {
        transactionCoordinator.rollback(context);
    }

    private static List<SqlProcessor> defaultProcessors() {
        return List.of(new ParameterProcessor(), new ExecutionProcessor(), new ResultProcessor());
    }
}
