package org.liteorm.test.interceptor;

import org.junit.jupiter.api.Test;
import org.liteorm.DefaultSqlEngine;
import org.liteorm.ExecutionContext;
import org.liteorm.api.ConnectionProvider;
import org.liteorm.api.ExecutionInterceptor;
import org.liteorm.api.ExecutionInvocation;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.SqlResult;
import org.liteorm.api.SqlTask;
import org.liteorm.runtime.SqlProcessor;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionInterceptorTest {

    @Test
    void invokesBeforeInOrderAndSuccessInReverseOrder() {
        List<String> events = new ArrayList<>();
        TrackingConnectionProvider connections = new TrackingConnectionProvider();
        DefaultSqlEngine engine = new DefaultSqlEngine(
            connections,
            List.of(successProcessor(connections.connection)),
            List.of(interceptor("first", events), interceptor("second", events))
        );

        SqlResult result = engine.execute(updatePlan());

        assertFalse(result.hasError());
        assertEquals(List.of("first.before", "second.before", "second.success", "first.success"), events);
        assertEquals(1, connections.releaseCount);
    }

    @Test
    void invokesFailureInReverseOrderAndPreservesOriginalFailure() {
        List<String> events = new ArrayList<>();
        TrackingConnectionProvider connections = new TrackingConnectionProvider();
        IllegalStateException failure = new IllegalStateException("processor failed");
        DefaultSqlEngine engine = new DefaultSqlEngine(
            connections,
            List.of(contextProcessor(connections.connection), (plan, context) -> { throw failure; }),
            List.of(interceptor("first", events), interceptor("second", events))
        );

        SqlResult result = engine.execute(updatePlan());

        assertTrue(result.hasError());
        assertSame(failure, result.getException());
        assertEquals(List.of("first.before", "second.before", "second.failure", "first.failure"), events);
        assertEquals(1, connections.releaseCount);
    }

    @Test
    void interceptorCallbackFailureDoesNotPreventCleanup() {
        TrackingConnectionProvider connections = new TrackingConnectionProvider();
        ExecutionInterceptor interceptor = new ExecutionInterceptor() {
            @Override
            public void afterSuccess(ExecutionInvocation invocation) {
                throw new IllegalStateException("callback failed");
            }
        };
        DefaultSqlEngine engine = new DefaultSqlEngine(
            connections, List.of(successProcessor(connections.connection)), List.of(interceptor));

        SqlResult result = engine.execute(updatePlan());

        assertTrue(result.hasError());
        assertEquals("callback failed", result.getException().getMessage());
        assertEquals(1, connections.releaseCount);
    }

    @Test
    void zeroInterceptorsPreserveDirectExecutionBehavior() {
        TrackingConnectionProvider connections = new TrackingConnectionProvider();
        DefaultSqlEngine engine = new DefaultSqlEngine(
            connections, List.of(successProcessor(connections.connection)), List.of());

        SqlResult result = engine.execute(updatePlan());

        assertFalse(result.hasError());
        assertEquals(3, result.getUpdateCount());
        assertEquals(1, connections.releaseCount);
    }

    private SqlProcessor successProcessor(Connection connection) {
        return (plan, context) -> {
            context.setConnection(connection);
            context.setUpdateCount(3);
        };
    }

    private SqlProcessor contextProcessor(Connection connection) {
        return (plan, context) -> context.setConnection(connection);
    }

    private ExecutionInterceptor interceptor(String name, List<String> events) {
        return new ExecutionInterceptor() {
            @Override
            public void beforeExecution(ExecutionInvocation invocation) {
                events.add(name + ".before");
                assertEquals("test.Mapper.update", invocation.statementId());
                assertEquals("UPDATE users SET name = ?", invocation.sql());
            }

            @Override
            public void afterSuccess(ExecutionInvocation invocation) {
                events.add(name + ".success");
                assertEquals(3, invocation.affectedRows());
                assertTrue(invocation.durationNanos() >= 0);
            }

            @Override
            public void afterFailure(ExecutionInvocation invocation) {
                events.add(name + ".failure");
                assertTrue(invocation.failure() instanceof IllegalStateException);
            }
        };
    }

    private ExecutionPlan updatePlan() {
        return new SqlTask(
            "test.Mapper.update",
            "UPDATE users SET name = ?",
            new Object[]{"Alice"},
            SqlTask.SqlType.UPDATE,
            true,
            "int",
            ExecutionPlan.SqlSource.ANNOTATION
        );
    }

    private static class TrackingConnectionProvider implements ConnectionProvider {
        private final Connection connection = (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                if (method.getName().equals("isClosed")) return false;
                if (method.getName().equals("close")) return null;
                if (method.getReturnType() == boolean.class) return false;
                if (method.getReturnType() == int.class) return 0;
                return null;
            });
        private int releaseCount;

        @Override public Connection acquire() { return connection; }
        @Override public void release(Connection connection) { releaseCount++; }
    }
}
