package org.liteorm.test.jdbc;

import org.junit.jupiter.api.Test;
import org.liteorm.api.ConnectionHandle;
import org.liteorm.api.ConnectionHandleFactory;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.RowCursor;
import org.liteorm.jdbc.JdbcSqlExecutor;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcCursorExecutionTest {

    @Test
    void readsRowsLazilyAndInvalidatesCursorAfterCallback() {
        List<String> events = new ArrayList<>();
        ResultSet rows = rows(events, List.of("Alice", "Bob"));
        AtomicReference<RowCursor<String>> captured = new AtomicReference<>();
        JdbcSqlExecutor executor = executor(events, statement(events, rows));

        String first = executor.queryCursor(plan(), (RowCursor<String> cursor) -> {
            captured.set(cursor);
            assertEquals(true, cursor.next());
            return cursor.current();
        });

        assertEquals("Alice", first);
        assertEquals(1, events.stream().filter("rows.next"::equals).count());
        assertEquals(List.of("rows.close", "statement.close", "transaction.close"),
            events.subList(events.size() - 3, events.size()));
        assertThrows(IllegalStateException.class, captured.get()::next);
        assertThrows(IllegalStateException.class, captured.get()::current);
    }

    @Test
    void callbackFailureStillClosesEveryOwnedResource() {
        List<String> events = new ArrayList<>();
        JdbcSqlExecutor executor = executor(events, statement(events, rows(events, List.of("Alice"))));

        assertThrows(IllegalArgumentException.class, () -> executor.queryCursor(
            plan(), (RowCursor<String> cursor) -> {
            cursor.next();
            throw new IllegalArgumentException("stop");
        }));

        assertEquals(List.of("rows.close", "statement.close", "transaction.close"),
            events.subList(events.size() - 3, events.size()));
    }

    @Test
    void rejectsNonSelectAndMissingRowMapperBeforeOpeningConnection() {
        List<String> events = new ArrayList<>();
        JdbcSqlExecutor executor = executor(events, statement(events, rows(events, List.of("Alice"))));
        ExecutionPlan update = new ExecutionPlan(
            "test.Mapper.update", "UPDATE users SET name = 'x'", new Object[0],
            ExecutionPlan.StatementType.UPDATE, ExecutionPlan.SqlSource.ANNOTATION,
            null, null, resultSet -> resultSet.getString(1));
        ExecutionPlan missingMapper = new ExecutionPlan(
            "test.Mapper.find", "SELECT name FROM users", new Object[0],
            ExecutionPlan.StatementType.SELECT, ExecutionPlan.SqlSource.ANNOTATION);

        assertThrows(IllegalArgumentException.class, () -> executor.queryCursor(update, cursor -> null));
        assertThrows(IllegalArgumentException.class, () -> executor.queryCursor(missingMapper, cursor -> null));
        assertEquals(List.of(), events);
    }

    private ExecutionPlan plan() {
        return new ExecutionPlan(
            "test.Mapper.scan", "SELECT name FROM users", new Object[0],
            ExecutionPlan.StatementType.SELECT, ExecutionPlan.SqlSource.ANNOTATION,
            null, null, resultSet -> resultSet.getString(1));
    }

    private JdbcSqlExecutor executor(List<String> events, PreparedStatement statement) {
        Connection connection = proxy(Connection.class, (method, args) -> {
            if (method.equals("prepareStatement")) {
                events.add("connection.prepare");
                return statement;
            }
            return null;
        });
        ConnectionHandleFactory factory = () -> new ConnectionHandle() {
            @Override
            public Connection connection() {
                events.add("transaction.connection");
                return connection;
            }

            @Override
            public void close() {
                events.add("transaction.close");
            }
        };
        return new JdbcSqlExecutor(() -> {
            events.add("transaction.open");
            return factory.openHandle();
        });
    }

    private PreparedStatement statement(List<String> events, ResultSet rows) {
        return proxy(PreparedStatement.class, (method, args) -> switch (method) {
            case "executeQuery" -> rows;
            case "close" -> { events.add("statement.close"); yield null; }
            default -> null;
        });
    }

    private ResultSet rows(List<String> events, List<String> values) {
        int[] index = {-1};
        return proxy(ResultSet.class, (method, args) -> switch (method) {
            case "next" -> { events.add("rows.next"); yield ++index[0] < values.size(); }
            case "getString" -> values.get(index[0]);
            case "close" -> { events.add("rows.close"); yield null; }
            default -> null;
        });
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> invocation.invoke(method.getName(), args == null ? new Object[0] : args));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args) throws Throwable;
    }
}
