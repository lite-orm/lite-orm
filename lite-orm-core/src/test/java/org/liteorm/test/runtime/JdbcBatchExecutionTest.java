package org.liteorm.test.runtime;

import org.junit.jupiter.api.Test;
import org.liteorm.DefaultSqlEngine;
import org.liteorm.api.BatchSqlTask;
import org.liteorm.api.ConnectionProvider;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.SqlResult;
import org.liteorm.api.SqlExecutionException;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.BatchUpdateException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcBatchExecutionTest {

    @Test
    void bindsEachParameterSetAndExecutesOneJdbcBatch() {
        List<String> events = new ArrayList<>();
        PreparedStatement statement = statement(events, new int[]{1, 1});
        Connection connection = connection(statement);
        DefaultSqlEngine engine = new DefaultSqlEngine(provider(connection));
        BatchSqlTask plan = new BatchSqlTask(
            "test.Mapper.insertBatch",
            "INSERT INTO users (id, name) VALUES (?, ?)",
            List.of(new Object[]{1L, "Alice"}, new Object[]{2L, "Bob"}),
            ExecutionPlan.SqlSource.ANNOTATION
        );

        SqlResult result = engine.execute(plan);

        assertArrayEquals(new int[]{1, 1}, result.getBatchUpdateCounts());
        assertEquals(List.of(
            "setObject:1:1", "setObject:2:Alice", "addBatch",
            "setObject:1:2", "setObject:2:Bob", "addBatch",
            "executeBatch", "statement.close"
        ), events);
    }

    @Test
    void emptyBatchReturnsNoCountsWithoutCallingJdbcExecuteBatch() {
        List<String> events = new ArrayList<>();
        PreparedStatement statement = statement(events, new int[0]);
        DefaultSqlEngine engine = new DefaultSqlEngine(provider(connection(statement)));
        BatchSqlTask plan = new BatchSqlTask(
            "test.Mapper.insertBatch",
            "INSERT INTO users (id, name) VALUES (?, ?)",
            List.of(),
            ExecutionPlan.SqlSource.XML
        );

        SqlResult result = engine.execute(plan);

        assertArrayEquals(new int[0], result.getBatchUpdateCounts());
        assertEquals(List.of("statement.close"), events);
    }

    @Test
    void partialFailureThrowsAndPreservesJdbcUpdateCounts() {
        List<String> events = new ArrayList<>();
        BatchUpdateException batchFailure = new BatchUpdateException("duplicate key", new int[]{1, -3});
        PreparedStatement statement = failingStatement(events, batchFailure);
        DefaultSqlEngine engine = new DefaultSqlEngine(provider(connection(statement)));
        BatchSqlTask plan = new BatchSqlTask(
            "test.Mapper.insertBatch",
            "INSERT INTO users (id, name) VALUES (?, ?)",
            List.of(new Object[]{1L, "Alice"}, new Object[]{1L, "Duplicate"}),
            ExecutionPlan.SqlSource.ANNOTATION
        );

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () -> engine.execute(plan));

        assertSame(batchFailure, failure.getCause().getCause());
        assertArrayEquals(new int[]{1, -3}, batchFailure.getUpdateCounts());
        assertEquals(List.of(
            "setObject:1:1", "setObject:2:Alice", "addBatch",
            "setObject:1:1", "setObject:2:Duplicate", "addBatch",
            "executeBatch", "statement.close"
        ), events);
    }

    private ConnectionProvider provider(Connection connection) {
        return new ConnectionProvider() {
            @Override
            public Connection acquire() {
                return connection;
            }

            @Override
            public void release(Connection releasedConnection) {
            }
        };
    }

    private Connection connection(PreparedStatement statement) {
        return (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{Connection.class},
            (proxy, method, args) -> method.getName().equals("prepareStatement")
                ? statement
                : primitiveDefault(method.getReturnType())
        );
    }

    private PreparedStatement statement(List<String> events, int[] updateCounts) {
        return (PreparedStatement) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{PreparedStatement.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "setObject" -> {
                    events.add("setObject:" + args[0] + ":" + args[1]);
                    yield null;
                }
                case "addBatch" -> {
                    events.add("addBatch");
                    yield null;
                }
                case "executeBatch" -> {
                    events.add("executeBatch");
                    yield updateCounts;
                }
                case "close" -> {
                    events.add("statement.close");
                    yield null;
                }
                default -> primitiveDefault(method.getReturnType());
            }
        );
    }

    private PreparedStatement failingStatement(List<String> events, BatchUpdateException failure) {
        return (PreparedStatement) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{PreparedStatement.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "setObject" -> {
                    events.add("setObject:" + args[0] + ":" + args[1]);
                    yield null;
                }
                case "addBatch" -> {
                    events.add("addBatch");
                    yield null;
                }
                case "executeBatch" -> {
                    events.add("executeBatch");
                    throw failure;
                }
                case "close" -> {
                    events.add("statement.close");
                    yield null;
                }
                default -> primitiveDefault(method.getReturnType());
            }
        );
    }

    private Object primitiveDefault(Class<?> type) {
        if (!type.isPrimitive()) return null;
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
