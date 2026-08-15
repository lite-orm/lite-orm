package org.liteorm;

import org.liteorm.api.ConnectionProvider;
import org.liteorm.api.ExecutionInterceptor;
import org.liteorm.api.SqlExecutor;
import org.liteorm.api.TransactionCoordinator;
import org.liteorm.runtime.ExecutionProcessor;
import org.liteorm.runtime.ParameterProcessor;
import org.liteorm.runtime.ResultProcessor;
import org.liteorm.runtime.SqlProcessor;

import java.util.List;
import java.util.Objects;

/**
 * Readable entry point for assembling LiteORM runtime components.
 */
public final class LiteOrm {

    private LiteOrm() {
    }

    public static StandaloneSqlEngine standalone(ConnectionProvider connectionProvider) {
        return new StandaloneSqlEngine(connectionProvider);
    }

    public static EngineBuilder engine(ConnectionProvider connectionProvider) {
        return new EngineBuilder(connectionProvider);
    }

    public static final class EngineBuilder {

        private final ConnectionProvider connectionProvider;
        private TransactionCoordinator transactionCoordinator = () -> null;
        private List<SqlProcessor> processors = List.of(
            new ParameterProcessor(),
            new ExecutionProcessor(),
            new ResultProcessor()
        );
        private List<ExecutionInterceptor> interceptors = List.of();

        private EngineBuilder(ConnectionProvider connectionProvider) {
            this.connectionProvider = Objects.requireNonNull(connectionProvider, "connectionProvider");
        }

        public EngineBuilder transactionCoordinator(TransactionCoordinator transactionCoordinator) {
            this.transactionCoordinator = Objects.requireNonNull(
                transactionCoordinator, "transactionCoordinator");
            return this;
        }

        public EngineBuilder processors(List<SqlProcessor> processors) {
            this.processors = List.copyOf(Objects.requireNonNull(processors, "processors"));
            return this;
        }

        public EngineBuilder interceptors(List<ExecutionInterceptor> interceptors) {
            this.interceptors = List.copyOf(Objects.requireNonNull(interceptors, "interceptors"));
            return this;
        }

        public SqlExecutor build() {
            return new DefaultSqlEngine(
                connectionProvider,
                transactionCoordinator,
                processors,
                interceptors
            );
        }
    }
}
