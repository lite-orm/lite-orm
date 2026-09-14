package io.github.lynxus.jdbc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.Month;
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
            method.equals("getString") ? "Alice" : null);
        TypeHandlerManager manager = new TypeHandlerManager();

        manager.setParameter(statement, 1, "Alice", String.class, null);
        String result = manager.resolveResult(metadata(JDBCType.VARCHAR), 1, String.class)
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
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> switch (method) {
            case "getString" -> "value";
            case "getMetaData" -> throw new SQLException(
                "Resolved handlers must not read metadata while mapping rows");
            default -> null;
        });

        TypeHandlerManager.ResultHandler<String> handler =
            new TypeHandlerManager().resolveResult(metadata, 1, String.class);
        assertEquals("value", handler.getResult(resultSet, 1));
        assertEquals("value", handler.getResult(resultSet, 1));

        assertEquals(1, metadataReads.get());
    }

    @Test
    void convertsEnumNamesAndOrdinalsToTheTargetEnum() throws Exception {
        AtomicReference<Object> value = new AtomicReference<>("ACTIVE");
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> switch (method) {
            case "getString", "getObject" -> value.get();
            default -> null;
        });
        TypeHandlerManager manager = new TypeHandlerManager();

        Status name = manager.resolveResult(metadata(JDBCType.VARCHAR), 1, Status.class)
            .getResult(resultSet, 1);
        value.set(1);
        Status ordinal = manager.resolveResult(metadata(JDBCType.INTEGER), 1, Status.class)
            .getResult(resultSet, 1);

        assertEquals(Status.ACTIVE, name);
        assertEquals(Status.DISABLED, ordinal);
    }

    @Test
    void preservesLocalTimePrecisionAndUsesOneBasedMonthValues() throws Exception {
        LocalTime preciseTime = LocalTime.of(12, 34, 56, 123_456_000);
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> switch (method) {
            case "getObject" -> arguments.length == 2 ? preciseTime : 9;
            default -> null;
        });
        TypeHandlerManager manager = new TypeHandlerManager();

        LocalTime time = manager.resolveResult(metadata(JDBCType.TIME), 1, LocalTime.class)
            .getResult(resultSet, 1);
        Month month = manager.resolveResult(metadata(JDBCType.INTEGER), 2, Month.class)
            .getResult(resultSet, 2);

        assertEquals(preciseTime, time);
        assertEquals(Month.SEPTEMBER, month);
    }

    @Test
    void usesDeterministicTimestampReadersForTemporalTargets() throws Exception {
        java.sql.Timestamp timestamp = java.sql.Timestamp.valueOf("2026-09-09 12:34:56.123456");
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> switch (method) {
            case "getTimestamp" -> timestamp;
            case "getObject" -> throw new SQLException(
                "Timestamp routes must not depend on the driver's default object type");
            default -> null;
        });
        TypeHandlerManager manager = new TypeHandlerManager();

        java.util.Date utilDate = manager
            .resolveResult(metadata(JDBCType.TIMESTAMP_WITH_TIMEZONE), 1, java.util.Date.class)
            .getResult(resultSet, 1);
        java.time.OffsetDateTime offsetDateTime = manager
            .resolveResult(metadata(JDBCType.TIMESTAMP), 2, java.time.OffsetDateTime.class)
            .getResult(resultSet, 2);

        assertEquals(timestamp.getTime(), utilDate.getTime());
        assertEquals(timestamp.toInstant(), offsetDateTime.toInstant());
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
    void rejectsNullParameterTypeWhenCreatingBinder() {
        NullPointerException failure = assertThrows(NullPointerException.class, () ->
            new TypeHandlerManager().parameterBinder(null, JDBCType.VARCHAR));

        assertEquals("javaType", failure.getMessage());
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

    @Test
    void routesUtilDateThroughDeclaredDateAndTimeJdbcTypes() throws Exception {
        AtomicReference<String> methodCalled = new AtomicReference<>();
        AtomicReference<Object> written = new AtomicReference<>();
        PreparedStatement statement = proxy(PreparedStatement.class, (method, arguments) -> {
            methodCalled.set(method);
            if (arguments.length > 1) {
                written.set(arguments[1]);
            }
            return null;
        });
        java.util.Date dateValue = new java.util.Date(1_725_004_800_000L);
        TypeHandlerManager manager = new TypeHandlerManager();

        manager.setParameter(statement, 1, dateValue, java.util.Date.class, JDBCType.DATE);
        assertEquals("setDate", methodCalled.get());
        assertEquals(dateValue.getTime(), ((java.sql.Date) written.get()).getTime());

        manager.setParameter(statement, 1, dateValue, java.util.Date.class, JDBCType.TIME);
        assertEquals("setTime", methodCalled.get());
        assertEquals(dateValue.getTime(), ((java.sql.Time) written.get()).getTime());

        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> switch (method) {
            case "getDate" -> new java.sql.Date(dateValue.getTime());
            case "getTime" -> new java.sql.Time(dateValue.getTime());
            case "getObject" -> arguments[0].equals(1)
                ? new java.sql.Date(dateValue.getTime())
                : new java.sql.Time(dateValue.getTime());
            default -> null;
        });
        java.util.Date dateResult = (java.util.Date) manager
            .resolveResult(metadata(JDBCType.DATE), 1, java.util.Date.class)
            .getResult(resultSet, 1);
        java.util.Date timeResult = (java.util.Date) manager
            .resolveResult(metadata(JDBCType.TIME), 2, java.util.Date.class)
            .getResult(resultSet, 2);

        assertEquals(dateValue.getTime(), dateResult.getTime());
        assertEquals(dateValue.getTime(), timeResult.getTime());
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
