package org.liteorm.test.runtime;

import org.junit.jupiter.api.Test;
import org.liteorm.DefaultSqlEngine;
import org.liteorm.api.ConnectionProvider;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.SqlExecutionException;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResourceLifecycleTest {

    @Test
    void closesResultSetStatementAndOrdinaryConnectionInReverseOwnershipOrder() {
        List<String> events = new ArrayList<>();
        Connection connection = connection(events);
        PreparedStatement statement = statement(events, null);
        ResultSet resultSet = resultSet(events, null);
        DefaultSqlEngine engine = engine(connection, events, context -> {
            context.setPreparedStatement(statement);
            context.setResultSet(resultSet);
            context.setQueryResults(List.of());
        });

        engine.execute(selectPlan());

        assertEquals(List.of("resultSet.close", "statement.close", "connection.release"), events);
    }

    @Test
    void cleanupFailuresAreSuppressedOntoThePrimaryExecutionFailure() {
        List<String> events = new ArrayList<>();
        SQLException resultSetCloseFailure = new SQLException("result set close failed");
        SQLException statementCloseFailure = new SQLException("statement close failed");
        RuntimeException releaseFailure = new RuntimeException("connection release failed");
        IllegalStateException executionFailure = new IllegalStateException("execution failed");
        Connection connection = connection(events);
        PreparedStatement statement = statement(events, statementCloseFailure);
        ResultSet resultSet = resultSet(events, resultSetCloseFailure);
        DefaultSqlEngine engine = engine(connection, events, releaseFailure, context -> {
            context.setPreparedStatement(statement);
            context.setResultSet(resultSet);
            throw executionFailure;
        });

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () -> engine.execute(selectPlan()));

        assertEquals("test.Mapper.find", failure.getStatementId());
        assertEquals(ExecutionPlan.SqlSource.XML, failure.getSourceType());
        assertEquals("SELECT secret FROM users WHERE id = ?", failure.getSql());
        assertFalse(failure.getMessage().contains("sensitive-value"));
        assertSame(executionFailure, failure.getCause());
        assertArrayEquals(
            new Throwable[]{resultSetCloseFailure, statementCloseFailure, releaseFailure},
            executionFailure.getSuppressed()
        );
        assertEquals(List.of("resultSet.close", "statement.close", "connection.release"), events);
    }

    @Test
    void cleanupFailureAfterSuccessfulExecutionBecomesThePrimarySqlFailure() {
        List<String> events = new ArrayList<>();
        SQLException resultSetCloseFailure = new SQLException("result set close failed");
        SQLException statementCloseFailure = new SQLException("statement close failed");
        RuntimeException releaseFailure = new RuntimeException("connection release failed");
        Connection connection = connection(events);
        PreparedStatement statement = statement(events, statementCloseFailure);
        ResultSet resultSet = resultSet(events, resultSetCloseFailure);
        DefaultSqlEngine engine = engine(connection, events, releaseFailure, context -> {
            context.setPreparedStatement(statement);
            context.setResultSet(resultSet);
            context.setQueryResults(List.of());
        });

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () -> engine.execute(selectPlan()));

        assertSame(resultSetCloseFailure, failure.getCause());
        assertArrayEquals(
            new Throwable[]{statementCloseFailure, releaseFailure},
            resultSetCloseFailure.getSuppressed()
        );
        assertEquals(List.of("resultSet.close", "statement.close", "connection.release"), events);
    }

    private DefaultSqlEngine engine(Connection connection, List<String> events, ContextAction action) {
        return engine(connection, events, null, action);
    }

    private DefaultSqlEngine engine(
            Connection connection,
            List<String> events,
            RuntimeException releaseFailure,
            ContextAction action) {
        ConnectionProvider provider = new ConnectionProvider() {
            @Override
            public Connection acquire() {
                return connection;
            }

            @Override
            public void release(Connection releasedConnection) {
                events.add("connection.release");
                if (releaseFailure != null) {
                    throw releaseFailure;
                }
            }
        };
        return new DefaultSqlEngine(provider, List.of((plan, context) -> action.run(context)), List.of());
    }

    private Connection connection(List<String> events) {
        return (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{Connection.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "isClosed" -> false;
                default -> primitiveDefault(method.getReturnType());
            }
        );
    }

    private PreparedStatement statement(List<String> events, SQLException closeFailure) {
        return (PreparedStatement) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{PreparedStatement.class},
            (proxy, method, args) -> {
                if (method.getName().equals("close")) {
                    events.add("statement.close");
                    if (closeFailure != null) throw closeFailure;
                }
                return primitiveDefault(method.getReturnType());
            }
        );
    }

    private ResultSet resultSet(List<String> events, SQLException closeFailure) {
        return (ResultSet) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{ResultSet.class},
            (proxy, method, args) -> {
                if (method.getName().equals("close")) {
                    events.add("resultSet.close");
                    if (closeFailure != null) throw closeFailure;
                }
                return primitiveDefault(method.getReturnType());
            }
        );
    }

    private ExecutionPlan selectPlan() {
        return new ExecutionPlan(
            "test.Mapper.find",
            "SELECT secret FROM users WHERE id = ?",
            new Object[]{"sensitive-value"},
            ExecutionPlan.StatementType.SELECT,
            ExecutionPlan.SqlSource.XML
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

    @FunctionalInterface
    private interface ContextAction {
        void run(org.liteorm.ExecutionContext context);
    }
}
