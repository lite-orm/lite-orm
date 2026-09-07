package org.liteorm.test.jdbc;

import org.junit.jupiter.api.Test;
import org.liteorm.api.TypeHandler;
import org.liteorm.jdbc.JdbcTypeRouter;
import org.liteorm.jdbc.StandardJdbcTypeMappings;

import java.lang.reflect.Proxy;
import java.sql.JDBCType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcTypeRouterTest {

    @Test
    void routesBothDirectionsThroughOneTypeHandler() throws Exception {
        AtomicReference<String> written = new AtomicReference<>();
        TypeHandler<String> handler = new TypeHandler<>() {
            @Override
            public void setNonNull(
                    PreparedStatement statement, int index, String value, JDBCType jdbcType) {
                written.set(value);
            }

            @Override
            public String getResult(ResultSet resultSet, int columnIndex) {
                return "handled";
            }
        };
        JdbcTypeRouter router = new JdbcTypeRouter(List.of(
            JdbcTypeRouter.mapping(String.class, JDBCType.CLOB, handler)));
        PreparedStatement statement = proxy(PreparedStatement.class, (method, arguments) -> null);
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (method, arguments) -> switch (method) {
            case "getColumnType" -> JDBCType.CLOB.getVendorTypeNumber();
            case "getColumnTypeName" -> "CLOB";
            default -> null;
        });
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> null);

        router.setParameter(statement, 1, "value", String.class, JDBCType.CLOB);
        TypeHandler<String> resolved = router.resolveResult(metadata, 1, String.class);

        assertEquals("value", written.get());
        assertSame(handler, resolved);
        assertEquals("handled", resolved.getResult(resultSet, 1));
    }

    @Test
    void resolvesResultMetadataOnceThenReusesTheHandler() throws Exception {
        AtomicInteger metadataReads = new AtomicInteger();
        TypeHandler<String> handler = new TypeHandler<>() {
            @Override
            public void setNonNull(
                    PreparedStatement statement, int index, String value, JDBCType jdbcType) {
            }

            @Override
            public String getResult(ResultSet resultSet, int columnIndex) {
                return "value";
            }
        };
        JdbcTypeRouter router = new JdbcTypeRouter(List.of(
            JdbcTypeRouter.mapping(String.class, JDBCType.VARCHAR, handler)));
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (method, arguments) -> switch (method) {
            case "getColumnType" -> {
                metadataReads.incrementAndGet();
                yield JDBCType.VARCHAR.getVendorTypeNumber();
            }
            case "getColumnTypeName" -> "VARCHAR";
            default -> null;
        });
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> null);

        TypeHandler<String> resolved = router.resolveResult(metadata, 1, String.class);
        resolved.getResult(resultSet, 1);
        resolved.getResult(resultSet, 1);

        assertEquals(1, metadataReads.get());
    }

    @Test
    void infersEnumNameOrOrdinalFromResultMetadata() throws Exception {
        JdbcTypeRouter router = new JdbcTypeRouter(List.of(
            JdbcTypeRouter.mapping(
                Status.class, JDBCType.VARCHAR,
                new StandardJdbcTypeMappings.EnumNameTypeHandler<>(Status::valueOf)),
            JdbcTypeRouter.mapping(
                Status.class, JDBCType.INTEGER,
                new StandardJdbcTypeMappings.EnumOrdinalTypeHandler<>(Status.values()))));
        AtomicReference<Object> value = new AtomicReference<>();
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> switch (method) {
            case "getObject" -> value.get();
            case "getString" -> value.get().toString();
            case "getInt" -> ((Number) value.get()).intValue();
            case "wasNull" -> value.get() == null;
            default -> null;
        });

        value.set("ACTIVE");
        TypeHandler<Status> nameHandler = router.resolveResult(
            metadata(JDBCType.VARCHAR), 1, Status.class);
        assertEquals(Status.ACTIVE, nameHandler.getResult(resultSet, 1));

        value.set(1);
        TypeHandler<Status> ordinalHandler = router.resolveResult(
            metadata(JDBCType.INTEGER), 1, Status.class);
        assertEquals(Status.DISABLED, ordinalHandler.getResult(resultSet, 1));
    }

    @Test
    void reportsActionableResultContextWhenNoRouteExists() {
        JdbcTypeRouter router = new JdbcTypeRouter(List.of());
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (method, arguments) -> switch (method) {
            case "getColumnType" -> JDBCType.OTHER.getVendorTypeNumber();
            case "getColumnTypeName" -> "money";
            case "getColumnLabel" -> "amount";
            default -> null;
        });

        SQLException failure = assertThrows(SQLException.class, () ->
            router.resolveResult(metadata, 1, Money.class));

        assertEquals(
            "No TypeHandler for " + Money.class.getName()
                + " + OTHER at result column 1 'amount' (vendor type money). "
                + "Add a matching @JdbcTypeMapping TypeHandler or use @UseRowMapper.",
            failure.getMessage());
    }

    @Test
    void prefersAnExactVendorTypeRouteOverAGenericJdbcRoute() throws Exception {
        TypeHandler<Money> genericHandler = handler(new Money(1));
        TypeHandler<Money> vendorHandler = handler(new Money(2));
        JdbcTypeRouter router = new JdbcTypeRouter(List.of(
            JdbcTypeRouter.mapping(Money.class, JDBCType.OTHER, genericHandler),
            JdbcTypeRouter.mapping(Money.class, JDBCType.OTHER, "money", vendorHandler)));
        ResultSetMetaData metadata = proxy(ResultSetMetaData.class, (method, arguments) -> switch (method) {
            case "getColumnType" -> JDBCType.OTHER.getVendorTypeNumber();
            case "getColumnTypeName" -> "MONEY";
            case "getColumnLabel" -> "amount";
            default -> null;
        });

        assertSame(vendorHandler, router.resolveResult(metadata, 1, Money.class));
    }

    @Test
    void rejectsAmbiguousCompatibleRoutes() {
        JdbcTypeRouter router = new JdbcTypeRouter(List.of(
            JdbcTypeRouter.mapping(byte[].class, JDBCType.BINARY, handler(new byte[0])),
            JdbcTypeRouter.mapping(byte[].class, JDBCType.LONGVARBINARY, handler(new byte[0]))));

        SQLException failure = assertThrows(SQLException.class, () ->
            router.resolveResult(metadata(JDBCType.VARBINARY), 1, byte[].class));

        assertTrue(failure.getMessage().contains("Ambiguous TypeHandlers"), failure::getMessage);
        assertTrue(failure.getMessage().contains("BINARY"), failure::getMessage);
        assertTrue(failure.getMessage().contains("LONGVARBINARY"), failure::getMessage);
    }

    @Test
    void doesNotTreatLifecycleJdbcTypesAsOrdinaryScalarRoutes() {
        JdbcTypeRouter router = new JdbcTypeRouter(List.of());

        SQLException failure = assertThrows(SQLException.class, () ->
            router.resolveResult(metadata(JDBCType.CLOB), 1, String.class));

        assertTrue(failure.getMessage().contains("No TypeHandler"), failure::getMessage);
    }

    @Test
    void writesCharacterUuidWithoutDiscoveringTheDatabaseProduct() throws Exception {
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

        new JdbcTypeRouter(List.of()).setParameter(
            statement, 1, value, UUID.class, JDBCType.VARCHAR);

        assertEquals("setString", methodCalled.get());
        assertEquals(value.toString(), written.get());
    }

    private <T> TypeHandler<T> handler(T result) {
        return new TypeHandler<>() {
            @Override
            public void setNonNull(
                    PreparedStatement statement, int index, T value, JDBCType jdbcType) {
            }

            @Override
            public T getResult(ResultSet resultSet, int columnIndex) {
                return result;
            }
        };
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
