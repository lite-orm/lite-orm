package org.liteorm.test.jdbc;

import org.junit.jupiter.api.Test;
import org.liteorm.api.ConnectionHandle;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.SqlResult;
import org.liteorm.jdbc.JdbcSqlExecutor;
import org.liteorm.jdbc.TypeHandlerManager;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JdbcSqlExecutorTypeRoutingTest {

    @Test
    void routesParametersAndReadsEachResultColumnTypeOncePerResultSet() {
        AtomicReference<String> written = new AtomicReference<>();
        AtomicInteger metadataTypeReads = new AtomicInteger();
        TypeHandlerManager manager = new TypeHandlerManager();
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (method, arguments) -> switch (method) {
            case "getColumnCount" -> 2;
            case "getColumnLabel", "getColumnName" -> (int) arguments[0] == 1 ? "name" : "created_at";
            case "getColumnType" -> {
                metadataTypeReads.incrementAndGet();
                yield JDBCType.VARCHAR.getVendorTypeNumber();
            }
            case "getColumnTypeName" -> "VARCHAR";
            default -> null;
        });
        int[] row = {-1};
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> switch (method) {
            case "getMetaData" -> metadata;
            case "next" -> ++row[0] < 2;
            case "getObject" -> "row" + row[0] + "-column" + arguments[0];
            default -> null;
        });
        PreparedStatement statement = proxy(PreparedStatement.class, (method, arguments) -> switch (method) {
            case "executeQuery" -> resultSet;
            case "setString" -> {
                written.set((String) arguments[1]);
                yield null;
            }
            default -> null;
        });
        Connection connection = proxy(Connection.class, (method, arguments) ->
            method.equals("prepareStatement") ? statement : null);
        ConnectionHandle handle = new ConnectionHandle() {
            @Override public Connection connection() { return connection; }
            @Override public void close() { }
        };
        ExecutionPlan.TypeRouting routing = new ExecutionPlan.TypeRouting(
            manager,
            new Class<?>[]{String.class},
            new JDBCType[]{JDBCType.VARCHAR},
            new Class<?>[]{String.class},
            null);
        ExecutionPlan plan = new ExecutionPlan(
            "test.Mapper.find", "SELECT name FROM users WHERE name = ?", new Object[]{"Alice"},
            ExecutionPlan.StatementType.SELECT, ExecutionPlan.SqlSource.GENERATED,
            null, null, null, null, routing);

        SqlResult result = new JdbcSqlExecutor(() -> handle).execute(plan);

        assertEquals("Alice", written.get());
        assertEquals(2, metadataTypeReads.get());
        assertEquals("row0-column1", result.getQueryResults().get(0)[0]);
        assertEquals("row0-column2", result.getQueryResults().get(0)[1]);
        assertEquals("row1-column1", result.getQueryResults().get(1)[0]);
        assertEquals("row1-column2", result.getQueryResults().get(1)[1]);
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[]{type},
            (proxy, method, arguments) -> invocation.invoke(
                method.getName(), arguments == null ? new Object[0] : arguments));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(String method, Object[] arguments) throws SQLException;
    }
}
