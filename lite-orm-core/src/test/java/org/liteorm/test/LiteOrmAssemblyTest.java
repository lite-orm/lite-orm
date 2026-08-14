package org.liteorm.test;

import org.junit.jupiter.api.Test;
import org.liteorm.DefaultSqlEngine;
import org.liteorm.LiteOrm;
import org.liteorm.StandaloneSqlEngine;
import org.liteorm.api.ConnectionProvider;
import org.liteorm.api.ExecutionInterceptor;
import org.liteorm.api.TransactionCoordinator;
import org.liteorm.runtime.ExecutionProcessor;
import org.liteorm.runtime.ParameterProcessor;
import org.liteorm.runtime.ResultProcessor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LiteOrmAssemblyTest {

    private final ConnectionProvider connectionProvider = new ConnectionProvider() {
        @Override
        public java.sql.Connection acquire() {
            return null;
        }

        @Override
        public void release(java.sql.Connection connection) {
        }
    };

    @Test
    void buildsStandaloneEngineThroughReadableFacade() {
        StandaloneSqlEngine engine = LiteOrm.standalone(connectionProvider);

        assertNotNull(engine);
    }

    @Test
    void buildsSharedEngineGraphWithExplicitStrategies() {
        TransactionCoordinator transactionCoordinator = () -> null;
        ExecutionInterceptor interceptor = new ExecutionInterceptor() {
        };

        assertInstanceOf(
            DefaultSqlEngine.class,
            LiteOrm.engine(connectionProvider)
                .transactionCoordinator(transactionCoordinator)
                .processors(List.of(
                    new ParameterProcessor(),
                    new ExecutionProcessor(),
                    new ResultProcessor()
                ))
                .interceptors(List.of(interceptor))
                .build()
        );
    }
}
