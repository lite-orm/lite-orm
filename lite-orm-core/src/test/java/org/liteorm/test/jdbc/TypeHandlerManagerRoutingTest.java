package org.liteorm.jdbc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypeHandlerManagerRoutingTest {

    @Test
    void handlesStandardValuesInBothDirections() throws Exception {
        AtomicReference<String> written = new AtomicReference<>();
        PreparedStatement statement = proxy(PreparedStatement.class, (method, arguments) -> {
            if (method.equals("setString")) {
                written.set((String) arguments[1]);
            }
            return null;
        });
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) ->
            method.equals("getObject") ? "Alice" : null);
        TypeHandlerManager manager = new TypeHandlerManager();

        manager.setParameter(statement, 1, "Alice", String.class, null);
        Object result = manager.resolveResult(metadata(JDBCType.VARCHAR), 1, String.class)
            .getResult(resultSet, 1);

        assertEquals("Alice", written.get());
        assertEquals("Alice", result);
    }

    @Test
    void resolvesResultMetadataOnceThenReusesTheHandler() throws Exception {
        AtomicInteger metadataReads = new AtomicInteger();
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (method, arguments) -> {
            if (method.equals("getColumnType")) {
                metadataReads.incrementAndGet();
                return JDBCType.VARCHAR.getVendorTypeNumber();
            }
            return null;
        });
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) ->
            method.equals("getObject") ? "value" : null);

        TypeHandlerManager.ResultHandler handler =
            new TypeHandlerManager().resolveResult(metadata, 1, String.class);
        handler.getResult(resultSet, 1);
        handler.getResult(resultSet, 1);

        assertEquals(1, metadataReads.get());
    }

    @Test
    void leavesEnumValuesForGeneratedEnumConversion() throws Exception {
        AtomicReference<Object> value = new AtomicReference<>("ACTIVE");
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) ->
            method.equals("getObject") ? value.get() : null);
        TypeHandlerManager manager = new TypeHandlerManager();

        Object name = manager.resolveResult(metadata(JDBCType.VARCHAR), 1, Status.class)
            .getResult(resultSet, 1);
        value.set(1);
        Object ordinal = manager.resolveResult(metadata(JDBCType.INTEGER), 1, Status.class)
            .getResult(resultSet, 1);

        assertEquals("ACTIVE", name);
        assertEquals(1, ordinal);
    }

    @Test
    void reportsRowMapperWhenNoStandardResultRouteExists() {
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (method, arguments) -> switch (method) {
            case "getColumnType" -> JDBCType.OTHER.getVendorTypeNumber();
            case "getColumnLabel" -> "amount";
            default -> null;
        });

        SQLException failure = assertThrows(SQLException.class, () ->
            new TypeHandlerManager().resolveResult(metadata, 1, Money.class));

        assertTrue(failure.getMessage().contains(Money.class.getName()), failure::getMessage);
        assertTrue(failure.getMessage().contains("OTHER"), failure::getMessage);
        assertTrue(failure.getMessage().contains("result column 1 'amount'"), failure::getMessage);
        assertTrue(failure.getMessage().contains("@UseRowMapper"), failure::getMessage);
    }

    @Test
    void reportsParameterBinderWhenNoStandardParameterRouteExists() {
        PreparedStatement statement = proxy(PreparedStatement.class, (method, arguments) -> null);

        SQLException failure = assertThrows(SQLException.class, () ->
            new TypeHandlerManager().setParameter(
                statement, 1, new Money(1), Money.class, JDBCType.OTHER));

        assertTrue(failure.getMessage().contains(Money.class.getName()), failure::getMessage);
        assertTrue(failure.getMessage().contains("@UseParameterBinder"), failure::getMessage);
    }

    @Test
    void bindsNullFromTheDeclaredJavaType() throws Exception {
        AtomicReference<Object> writtenJdbcType = new AtomicReference<>();
        PreparedStatement statement = proxy(PreparedStatement.class, (method, arguments) -> {
            if (method.equals("setNull")) {
                writtenJdbcType.set(arguments[1]);
            }
            return null;
        });

        new TypeHandlerManager().setParameter(statement, 1, null, String.class, null);

        assertEquals(JDBCType.VARCHAR.getVendorTypeNumber(), writtenJdbcType.get());
    }

    @Test
    void rejectsLifecycleJdbcTypesFromStandardScalarRouting() {
        SQLException failure = assertThrows(SQLException.class, () ->
            new TypeHandlerManager().resolveResult(metadata(JDBCType.CLOB), 1, String.class));

        assertTrue(failure.getMessage().contains("No standard TypeHandler"), failure::getMessage);
        assertTrue(failure.getMessage().contains("@UseRowMapper"), failure::getMessage);
    }

    @Test
    void writesCharacterUuidWithoutDatabaseProductRouting() throws Exception {
        AtomicReference<String> methodCalled = new AtomicReference<>();
        AtomicReference<Object> written = new AtomicReference<>();
        PreparedStatement statement = proxy(PreparedStatement.class, (method, arguments) -> {
            methodCalled.set(method);
            if (arguments.length > 1) {
                written.set(arguments[1]);
            }
            return null;
        });
        UUID value = UUID.randomUUID();

        new TypeHandlerManager().setParameter(
            statement, 1, value, UUID.class, JDBCType.VARCHAR);

        assertEquals("setString", methodCalled.get());
        assertEquals(value.toString(), written.get());
    }

    private ResultSetMetaData metadata(JDBCType jdbcType) {
        return proxy(ResultSetMetaData.class, (method, arguments) -> switch (method) {
            case "getColumnType" -> jdbcType.getVendorTypeNumber();
            case "getColumnTypeName" -> jdbcType.getName();
            default -> null;
        });
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

    private enum Status {
        ACTIVE,
        DISABLED
    }

    private record Money(long minorUnits) {
    }
}
