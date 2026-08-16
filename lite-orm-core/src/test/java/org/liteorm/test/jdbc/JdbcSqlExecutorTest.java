package org.liteorm.test.jdbc;

import org.junit.jupiter.api.Test;
import org.liteorm.api.BatchExecutionPlan;
import org.liteorm.api.ConnectionHandle;
import org.liteorm.api.ConnectionHandleFactory;
import org.liteorm.api.ExecutionInterceptor;
import org.liteorm.api.ExecutionOutcome;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.JdbcExecutionState;
import org.liteorm.api.ParameterBinder;
import org.liteorm.api.StatementOptions;
import org.liteorm.api.SqlExecutionException;
import org.liteorm.api.SqlResult;
import org.liteorm.jdbc.JdbcSqlExecutor;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JdbcSqlExecutorTest {

    @Test
    void appliesConfiguredStatementOptionsBeforeBindingAndExecution() {
        List<String> events = new ArrayList<>();
        ResultSet rows = rows(events, List.<Object[]>of(new Object[]{1L, "Alice"}));
        PreparedStatement statement = statement(events, rows, 0, null, null);
        ExecutionPlan plan = new ExecutionPlan(
            "test.Mapper.find", "SELECT id, name FROM users WHERE id = ?",
            new Object[]{7L}, ExecutionPlan.StatementType.SELECT, ExecutionPlan.SqlSource.XML,
            false, null, null, new StatementOptions(3, 100, 25));

        executor(events, statement).execute(plan);

        assertEquals(List.of(
            "setQueryTimeout:3", "setFetchSize:100", "setMaxRows:25",
            "setObject:1:7", "executeQuery"
        ), events.subList(3, 8));
    }

    @Test
    void defaultStatementOptionsDoNotCallJdbcSetters() {
        List<String> events = new ArrayList<>();
        ResultSet rows = rows(events, List.<Object[]>of(new Object[]{1L, "Alice"}));
        PreparedStatement statement = statement(events, rows, 0, null, null);

        executor(events, statement).execute(selectPlan(null));

        assertFalse(events.stream().anyMatch(event -> event.startsWith("setQueryTimeout:")));
        assertFalse(events.stream().anyMatch(event -> event.startsWith("setFetchSize:")));
        assertFalse(events.stream().anyMatch(event -> event.startsWith("setMaxRows:")));
    }

    @Test
    void rejectsInvalidStatementOptions() {
        assertThrows(IllegalArgumentException.class, () -> new StatementOptions(0, null, null));
        assertThrows(IllegalArgumentException.class, () -> new StatementOptions(-1, null, null));
        assertThrows(IllegalArgumentException.class, () -> new StatementOptions(null, 0, null));
        assertThrows(IllegalArgumentException.class, () -> new StatementOptions(null, -1, null));
        assertThrows(IllegalArgumentException.class, () -> new StatementOptions(null, null, -1));

        assertEquals(new StatementOptions(1, 1, 0), new StatementOptions(1, 1, 0));
    }

    @Test
    void validatesBeforeOpeningTransaction() {
        TrackingFactory transactions = new TrackingFactory(null, new ArrayList<>());
        ExecutionPlan invalid = new ExecutionPlan(
            "test.Mapper.batch", "INSERT INTO values_table VALUES (?)", new Object[0],
            ExecutionPlan.StatementType.BATCH, ExecutionPlan.SqlSource.GENERATED);

        assertThrows(IllegalArgumentException.class, () ->
            new JdbcSqlExecutor(transactions).execute(invalid));

        assertEquals(0, transactions.openCount);
    }

    @Test
    void selectsRawRowsAndClosesResourcesInOwnershipOrder() {
        List<String> events = new ArrayList<>();
        ResultSet rows = rows(events, List.of(new Object[]{1L, "Alice"}, new Object[]{2L, "Bob"}));
        PreparedStatement statement = statement(events, rows, 0, null, null);
        JdbcSqlExecutor executor = executor(events, statement);

        SqlResult result = executor.execute(selectPlan(null));

        assertArrayEquals(new Object[]{1L, "Alice"}, result.getQueryResults().get(0));
        assertArrayEquals(new Object[]{2L, "Bob"}, result.getQueryResults().get(1));
        assertEquals(List.of(
            "transaction.open", "transaction.connection", "connection.prepare",
            "setObject:1:7", "executeQuery",
            "rows.next:true", "rows.getObject:1", "rows.getObject:2",
            "rows.next:true", "rows.getObject:1", "rows.getObject:2", "rows.next:false",
            "rows.close", "statement.close", "transaction.close"
        ), events);
    }

    @Test
    void executesInsertUpdateAndDeleteAsJdbcUpdates() {
        for (ExecutionPlan.StatementType type : List.of(
                ExecutionPlan.StatementType.INSERT,
                ExecutionPlan.StatementType.UPDATE,
                ExecutionPlan.StatementType.DELETE)) {
            List<String> events = new ArrayList<>();
            PreparedStatement statement = statement(events, null, 3, null, null);

            SqlResult result = executor(events, statement).execute(writePlan(type, false, null));

            assertEquals(3, result.getUpdateCount());
            assertEquals(1, events.stream().filter("executeUpdate"::equals).count());
        }
    }

    @Test
    void requestsAndExtractsExactlyOneGeneratedKey() {
        List<String> events = new ArrayList<>();
        ResultSet keys = generatedKeys(events, 42L);
        PreparedStatement statement = statement(events, null, 1, keys, null);

        SqlResult result = executor(events, statement).execute(
            writePlan(ExecutionPlan.StatementType.INSERT, true, null));

        assertEquals(42L, result.getGeneratedKey());
        assertEquals(Statement.RETURN_GENERATED_KEYS,
            events.stream().filter(event -> event.startsWith("connection.prepare:")).findFirst()
                .map(event -> Integer.parseInt(event.substring(event.indexOf(':') + 1))).orElseThrow());
        assertEquals(List.of("keys.close", "statement.close", "transaction.close"),
            events.subList(events.size() - 3, events.size()));
    }

    @Test
    void bindsEveryBatchRowAndExecutesOneBatch() {
        List<String> events = new ArrayList<>();
        PreparedStatement statement = statement(events, null, 0, null, new int[]{1, 1});
        BatchExecutionPlan plan = new BatchExecutionPlan(
            "test.Mapper.insertAll", "INSERT INTO users(id, name) VALUES (?, ?)",
            List.of(new Object[]{1L, "Alice"}, new Object[]{2L, "Bob"}),
            ExecutionPlan.SqlSource.GENERATED);

        SqlResult result = executor(events, statement).execute(plan);

        assertArrayEquals(new int[]{1, 1}, result.getBatchUpdateCounts());
        assertEquals(List.of(
            "setObject:1:1", "setObject:2:Alice", "addBatch",
            "setObject:1:2", "setObject:2:Bob", "addBatch", "executeBatch"
        ), events.subList(3, 10));
    }

    @Test
    void emptyBatchReturnsEmptyCountsWithoutJdbcExecution() {
        List<String> events = new ArrayList<>();
        PreparedStatement statement = statement(events, null, 0, null, new int[]{99});
        BatchExecutionPlan plan = new BatchExecutionPlan(
            "test.Mapper.insertAll", "INSERT INTO users(id) VALUES (?)",
            List.of(), ExecutionPlan.SqlSource.GENERATED);

        SqlResult result = executor(events, statement).execute(plan);

        assertArrayEquals(new int[0], result.getBatchUpdateCounts());
        assertFalse(events.contains("executeBatch"));
    }

    @Test
    void usesCustomBinderAndRowMapperWithoutReflection() {
        List<String> bindEvents = new ArrayList<>();
        PreparedStatement writeStatement = statement(bindEvents, null, 1, null, null);
        ParameterBinder<String> binder = (target, index, value) -> {
            bindEvents.add("binder:" + index + ":" + value);
            target.setNull(index, Types.VARCHAR);
        };

        executor(bindEvents, writeStatement).execute(
            writePlan(ExecutionPlan.StatementType.INSERT, false, binder));

        assertEquals(List.of("binder:1:null", "setNull:1:" + Types.VARCHAR),
            bindEvents.subList(3, 5));

        List<String> rowEvents = new ArrayList<>();
        ResultSet resultSet = singleColumnRows(rowEvents, List.of("Alice", "Bob"));
        SqlResult result = executor(rowEvents, statement(rowEvents, resultSet, 0, null, null))
            .execute(selectPlan(current -> current.getString(1).toUpperCase()));

        assertArrayEquals(new Object[]{"ALICE"}, result.getQueryResults().get(0));
        assertArrayEquals(new Object[]{"BOB"}, result.getQueryResults().get(1));
    }

    @Test
    void invokesInterceptorsAroundExecutionInDeterministicOrder() {
        List<String> events = new ArrayList<>();
        ExecutionInterceptor first = interceptor("first", events);
        ExecutionInterceptor second = interceptor("second", events);
        PreparedStatement statement = statement(new ArrayList<>(), null, 3, null, null);
        JdbcSqlExecutor executor = new JdbcSqlExecutor(
            new TrackingFactory(connection(new ArrayList<>(), statement), new ArrayList<>()),
            List.of(first, second));

        executor.execute(writePlan(ExecutionPlan.StatementType.UPDATE, false, null));

        assertEquals(List.of("first.before", "second.before", "second.success:3", "first.success:3"), events);
    }

    @Test
    void terminalSuccessCallbackFailureDoesNotChangeTheSqlResult() {
        List<String> events = new ArrayList<>();
        ExecutionInterceptor first = interceptor("first", events);
        ExecutionInterceptor failing = new ExecutionInterceptor() {
            @Override
            public void beforeExecution(ExecutionPlan plan) {
                events.add("failing.before");
            }

            @Override
            public void afterSuccess(ExecutionOutcome outcome) {
                events.add("failing.success");
                throw new IllegalStateException("observer failed");
            }
        };
        PreparedStatement statement = statement(new ArrayList<>(), null, 3, null, null);
        JdbcSqlExecutor executor = new JdbcSqlExecutor(
            new TrackingFactory(connection(new ArrayList<>(), statement), new ArrayList<>()),
            List.of(first, failing));

        SqlResult result = executor.execute(writePlan(ExecutionPlan.StatementType.UPDATE, false, null));

        assertEquals(3, result.getUpdateCount());
        assertEquals(List.of(
            "first.before", "failing.before", "failing.success", "first.success:3"
        ), events);
    }

    @Test
    void beforeFailureUnwindsOnlyInterceptorsThatEnteredSuccessfully() {
        List<String> events = new ArrayList<>();
        ExecutionInterceptor first = interceptor("first", events);
        ExecutionInterceptor failing = new ExecutionInterceptor() {
            @Override
            public void beforeExecution(ExecutionPlan plan) {
                events.add("failing.before");
                throw new IllegalStateException("veto");
            }

            @Override
            public void afterFailure(ExecutionOutcome outcome) {
                events.add("failing.failure");
            }
        };

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            new JdbcSqlExecutor(() -> null, List.of(first, failing)).execute(selectPlan(null)));

        assertEquals(JdbcExecutionState.NOT_EXECUTED, failure.getExecutionState());
        assertEquals(List.of("first.before", "failing.before", "first.failure"), events);
    }

    @Test
    void unwindsFailureInterceptorsInReverseOrder() {
        List<String> events = new ArrayList<>();
        ExecutionInterceptor first = interceptor("first", events);
        ExecutionInterceptor second = interceptor("second", events);
        ConnectionHandleFactory transactions = () -> new ConnectionHandle() {
            @Override public Connection connection() { throw new IllegalStateException("failed"); }
            @Override public void close() { }
        };

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            new JdbcSqlExecutor(transactions, List.of(first, second)).execute(selectPlan(null)));

        assertEquals(JdbcExecutionState.NOT_EXECUTED, failure.getExecutionState());
        assertEquals(List.of("first.before", "second.before", "second.failure", "first.failure"), events);
    }

    @Test
    void reportsUnknownOutcomeWhenJdbcExecuteThrows() {
        SQLException executeFailure = new SQLException("execute failed");
        PreparedStatement statement = proxy(PreparedStatement.class, (method, args) -> switch (method) {
            case "setObject", "close" -> null;
            case "executeUpdate" -> throw executeFailure;
            default -> null;
        });

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            executor(new ArrayList<>(), statement)
                .execute(writePlan(ExecutionPlan.StatementType.UPDATE, false, null)));

        assertSame(executeFailure, failure.getCause());
        assertEquals(JdbcExecutionState.OUTCOME_UNKNOWN, failure.getExecutionState());
    }

    @Test
    void reportsExecutedWhenCleanupFailsAfterJdbcReturns() {
        SQLException closeFailure = new SQLException("statement close failed");
        PreparedStatement statement = statement(new ArrayList<>(), null, 1, null, null, closeFailure);

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            executor(new ArrayList<>(), statement)
                .execute(writePlan(ExecutionPlan.StatementType.UPDATE, false, null)));

        assertSame(closeFailure, failure.getCause());
        assertEquals(JdbcExecutionState.EXECUTED, failure.getExecutionState());
    }

    @Test
    void preservesPrimaryFailureAndFlattensCallbackAndCleanupFailures() {
        SQLException executionFailure = new SQLException("read failed");
        SQLException resultSetCloseFailure = new SQLException("rows close failed");
        SQLException statementCloseFailure = new SQLException("statement close failed");
        IllegalStateException transactionCloseFailure = new IllegalStateException("transaction close failed");
        IllegalArgumentException callbackFailure = new IllegalArgumentException("callback failed");
        ResultSet resultSet = failingRows(executionFailure, resultSetCloseFailure);
        PreparedStatement statement = statement(new ArrayList<>(), resultSet, 0, null, null, statementCloseFailure);
        Connection connection = connection(new ArrayList<>(), statement);
        ConnectionHandleFactory transactions = () -> connectionHandle(
            connection, transactionCloseFailure, new ArrayList<>());
        ExecutionInterceptor interceptor = new ExecutionInterceptor() {
            @Override
            public void afterFailure(ExecutionOutcome outcome) {
                throw callbackFailure;
            }
        };

        SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
            new JdbcSqlExecutor(transactions, List.of(interceptor)).execute(selectPlan(null)));

        assertSame(executionFailure, failure.getCause());
        assertEquals(JdbcExecutionState.EXECUTED, failure.getExecutionState());
        assertArrayEquals(
            new Throwable[]{resultSetCloseFailure, statementCloseFailure, transactionCloseFailure},
            executionFailure.getSuppressed());
        assertFalse(failure.getMessage().contains("customer-secret"));
    }

    private ExecutionInterceptor interceptor(String name, List<String> events) {
        return new ExecutionInterceptor() {
            @Override public void beforeExecution(ExecutionPlan plan) { events.add(name + ".before"); }
            @Override public void afterSuccess(ExecutionOutcome outcome) {
                events.add(name + ".success:" + outcome.affectedRows());
            }
            @Override public void afterFailure(ExecutionOutcome outcome) { events.add(name + ".failure"); }
        };
    }

    private JdbcSqlExecutor executor(List<String> events, PreparedStatement statement) {
        return new JdbcSqlExecutor(new TrackingFactory(connection(events, statement), events));
    }

    private ExecutionPlan selectPlan(org.liteorm.api.RowMapper<?> rowMapper) {
        return new ExecutionPlan(
            "test.Mapper.find", "SELECT id, name FROM users WHERE id = ?",
            new Object[]{7L}, ExecutionPlan.StatementType.SELECT, ExecutionPlan.SqlSource.XML,
            false, null, rowMapper);
    }

    private ExecutionPlan writePlan(
            ExecutionPlan.StatementType type, boolean generatedKey, ParameterBinder<?> binder) {
        return new ExecutionPlan(
            "test.Mapper.write", "INSERT INTO users(name) VALUES (?)", new Object[]{binder == null ? "Alice" : null},
            type, ExecutionPlan.SqlSource.ANNOTATION, generatedKey,
            binder == null ? null : new ParameterBinder<?>[]{binder}, null);
    }

    private Connection connection(List<String> events, PreparedStatement statement) {
        return proxy(Connection.class, (method, args) -> {
            if (method.equals("prepareStatement")) {
                events.add(args.length == 2 ? "connection.prepare:" + args[1] : "connection.prepare");
                return statement;
            }
            return null;
        });
    }

    private PreparedStatement statement(
            List<String> events, ResultSet rows, int updateCount, ResultSet keys, int[] batchCounts) {
        return statement(events, rows, updateCount, keys, batchCounts, null);
    }

    private PreparedStatement statement(
            List<String> events,
            ResultSet rows,
            int updateCount,
            ResultSet keys,
            int[] batchCounts,
            SQLException closeFailure) {
        return proxy(PreparedStatement.class, (method, args) -> switch (method) {
            case "setQueryTimeout" -> { events.add("setQueryTimeout:" + args[0]); yield null; }
            case "setFetchSize" -> { events.add("setFetchSize:" + args[0]); yield null; }
            case "setMaxRows" -> { events.add("setMaxRows:" + args[0]); yield null; }
            case "setObject" -> { events.add("setObject:" + args[0] + ":" + args[1]); yield null; }
            case "setNull" -> { events.add("setNull:" + args[0] + ":" + args[1]); yield null; }
            case "executeQuery" -> { events.add("executeQuery"); yield rows; }
            case "executeUpdate" -> { events.add("executeUpdate"); yield updateCount; }
            case "getGeneratedKeys" -> { events.add("getGeneratedKeys"); yield keys; }
            case "addBatch" -> { events.add("addBatch"); yield null; }
            case "executeBatch" -> { events.add("executeBatch"); yield batchCounts; }
            case "close" -> { events.add("statement.close"); if (closeFailure != null) throw closeFailure; yield null; }
            default -> null;
        });
    }

    private ResultSet rows(List<String> events, List<Object[]> values) {
        int[] cursor = {-1};
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class,
            (method, args) -> method.equals("getColumnCount") ? values.get(0).length : null);
        return proxy(ResultSet.class, (method, args) -> switch (method) {
            case "getMetaData" -> metadata;
            case "next" -> { boolean present = ++cursor[0] < values.size(); events.add("rows.next:" + present); yield present; }
            case "getObject" -> { events.add("rows.getObject:" + args[0]); yield values.get(cursor[0])[(int) args[0] - 1]; }
            case "close" -> { events.add("rows.close"); yield null; }
            default -> null;
        });
    }

    private ResultSet singleColumnRows(List<String> events, List<String> values) {
        int[] cursor = {-1};
        return proxy(ResultSet.class, (method, args) -> switch (method) {
            case "next" -> ++cursor[0] < values.size();
            case "getString" -> values.get(cursor[0]);
            case "close" -> { events.add("rows.close"); yield null; }
            default -> null;
        });
    }

    private ResultSet generatedKeys(List<String> events, Object key) {
        int[] cursor = {-1};
        return proxy(ResultSet.class, (method, args) -> switch (method) {
            case "next" -> ++cursor[0] == 0;
            case "getObject" -> key;
            case "close" -> { events.add("keys.close"); yield null; }
            default -> null;
        });
    }

    private ResultSet failingRows(SQLException readFailure, SQLException closeFailure) {
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class,
            (method, args) -> method.equals("getColumnCount") ? 1 : null);
        return proxy(ResultSet.class, (method, args) -> switch (method) {
            case "getMetaData" -> metadata;
            case "next" -> true;
            case "getObject" -> throw readFailure;
            case "close" -> throw closeFailure;
            default -> null;
        });
    }

    private ConnectionHandle connectionHandle(
            Connection connection, RuntimeException closeFailure, List<String> events) {
        return new ConnectionHandle() {
            @Override public Connection connection() {
                events.add("transaction.connection");
                return connection;
            }
            @Override public void close() { events.add("transaction.close"); if (closeFailure != null) throw closeFailure; }
        };
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Invocation action) {
        return (T) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            Object result = action.invoke(method.getName(), args == null ? new Object[0] : args);
            if (result != null || !method.getReturnType().isPrimitive()) return result;
            if (method.getReturnType() == boolean.class) return false;
            if (method.getReturnType() == byte.class) return (byte) 0;
            if (method.getReturnType() == short.class) return (short) 0;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == long.class) return 0L;
            if (method.getReturnType() == float.class) return 0F;
            if (method.getReturnType() == double.class) return 0D;
            if (method.getReturnType() == char.class) return '\0';
            return null;
        });
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] args) throws Throwable;
    }

    private final class TrackingFactory implements ConnectionHandleFactory {
        private final Connection connection;
        private final List<String> events;
        private int openCount;

        private TrackingFactory(Connection connection, List<String> events) {
            this.connection = connection;
            this.events = events;
        }

        @Override
        public ConnectionHandle openHandle() {
            openCount++;
            events.add("transaction.open");
            return connectionHandle(connection, null, events);
        }
    }
}
