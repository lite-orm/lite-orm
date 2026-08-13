package org.liteorm.test;

import org.junit.jupiter.api.Test;
import org.liteorm.StandaloneSqlEngine;
import org.liteorm.api.ConnectionProvider;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.SqlResult;
import org.liteorm.api.SqlTask;
import org.liteorm.api.TransactionContext;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class DefaultSqlEngineTransactionAssemblyTest {

    @Test
    void customProcessorChainReceivesTheActiveLocalTransactionConnection() throws Exception {
        TrackingConnectionProvider connections = new TrackingConnectionProvider();
        StandaloneSqlEngine engine = new StandaloneSqlEngine(
            connections,
            List.of((plan, context) -> {
                assertSame(connections.connection, context.getConnection());
                context.setUpdateCount(1);
            }),
            List.of()
        );
        TransactionContext transaction = engine.begin();

        try {
            SqlResult result = engine.execute(new SqlTask(
                "test.Mapper.update",
                "UPDATE users SET name = ?",
                new Object[]{"Alice"},
                SqlTask.SqlType.UPDATE,
                true,
                "int",
                ExecutionPlan.SqlSource.ANNOTATION
            ));

            assertFalse(result.hasError());
            assertSame(transaction.getConnection(), connections.connection);
        } finally {
            engine.rollback(transaction);
        }
    }

    private static final class TrackingConnectionProvider implements ConnectionProvider {
        private final AtomicInteger acquisitions = new AtomicInteger();
        private final Connection connection = (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{Connection.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "setAutoCommit", "rollback", "close" -> null;
                case "isClosed" -> false;
                default -> method.getReturnType().isPrimitive() ? primitiveDefault(method.getReturnType()) : null;
            }
        );

        @Override
        public Connection acquire() {
            acquisitions.incrementAndGet();
            return connection;
        }

        @Override
        public void release(Connection connection) {
        }

        private static Object primitiveDefault(Class<?> type) {
            if (type == boolean.class) return false;
            if (type == char.class) return '\0';
            if (type == byte.class) return (byte) 0;
            if (type == short.class) return (short) 0;
            if (type == int.class) return 0;
            if (type == long.class) return 0L;
            if (type == float.class) return 0F;
            if (type == double.class) return 0D;
            return null;
        }
    }
}
