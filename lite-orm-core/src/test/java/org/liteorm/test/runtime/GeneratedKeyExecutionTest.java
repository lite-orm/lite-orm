package org.liteorm.test.runtime;

import org.junit.jupiter.api.Test;
import org.liteorm.DefaultSqlEngine;
import org.liteorm.api.ConnectionProvider;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.SqlResult;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GeneratedKeyExecutionTest {

    @Test
    void requestsReadsAndClosesOneGeneratedKey() {
        List<String> events = new ArrayList<>();
        ResultSet generatedKeys = generatedKeys(events, 42L);
        PreparedStatement statement = statement(events, generatedKeys);
        Connection connection = connection(events, statement);
        DefaultSqlEngine engine = new DefaultSqlEngine(provider(events, connection));
        ExecutionPlan plan = new ExecutionPlan(
            "test.Mapper.insert",
            "INSERT INTO users (name) VALUES (?)",
            new Object[]{"Alice"},
            ExecutionPlan.StatementType.INSERT,
            ExecutionPlan.SqlSource.ANNOTATION,
            true,
            null,
            null
        );

        SqlResult result = engine.execute(plan);

        assertEquals(42L, result.getGeneratedKey());
        assertEquals(List.of(
            "prepareStatement:" + Statement.RETURN_GENERATED_KEYS,
            "setObject:1:Alice",
            "executeUpdate",
            "getGeneratedKeys",
            "generatedKeys.next:true",
            "generatedKeys.getObject:1",
            "generatedKeys.next:false",
            "generatedKeys.close",
            "statement.close",
            "connection.release"
        ), events);
    }

    private ConnectionProvider provider(List<String> events, Connection connection) {
        return new ConnectionProvider() {
            @Override
            public Connection acquire() {
                return connection;
            }

            @Override
            public void release(Connection releasedConnection) {
                events.add("connection.release");
            }
        };
    }

    private Connection connection(List<String> events, PreparedStatement statement) {
        return (Connection) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{Connection.class},
            (proxy, method, args) -> {
                if (method.getName().equals("prepareStatement")) {
                    events.add("prepareStatement:" + (args.length == 2 ? args[1] : "default"));
                    return statement;
                }
                return primitiveDefault(method.getReturnType());
            }
        );
    }

    private PreparedStatement statement(List<String> events, ResultSet generatedKeys) {
        return (PreparedStatement) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{PreparedStatement.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "setObject" -> {
                    events.add("setObject:" + args[0] + ":" + args[1]);
                    yield null;
                }
                case "executeUpdate" -> {
                    events.add("executeUpdate");
                    yield 1;
                }
                case "getGeneratedKeys" -> {
                    events.add("getGeneratedKeys");
                    yield generatedKeys;
                }
                case "close" -> {
                    events.add("statement.close");
                    yield null;
                }
                default -> primitiveDefault(method.getReturnType());
            }
        );
    }

    private ResultSet generatedKeys(List<String> events, Object key) {
        int[] cursor = {-1};
        return (ResultSet) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{ResultSet.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "next" -> {
                    cursor[0]++;
                    boolean present = cursor[0] == 0;
                    events.add("generatedKeys.next:" + present);
                    yield present;
                }
                case "getObject" -> {
                    events.add("generatedKeys.getObject:" + args[0]);
                    yield key;
                }
                case "close" -> {
                    events.add("generatedKeys.close");
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
