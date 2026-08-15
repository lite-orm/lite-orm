package org.liteorm.test.runtime;

import org.junit.jupiter.api.Test;
import org.liteorm.DefaultSqlEngine;
import org.liteorm.api.ConnectionProvider;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.ParameterBinder;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NullableParameterBinderTest {

    @Test
    void customBinderOwnsNullJdbcBinding() {
        List<String> events = new ArrayList<>();
        PreparedStatement statement = statement(events);
        Connection connection = connection(statement);
        ParameterBinder<String> nullableBinder = (preparedStatement, index, value) -> {
            events.add("binder:" + index + ":" + value);
            preparedStatement.setNull(index, Types.VARCHAR);
        };
        ExecutionPlan plan = new ExecutionPlan(
            "test.Mapper.insert",
            "INSERT INTO values_table (value) VALUES (?)",
            new Object[]{null},
            ExecutionPlan.StatementType.INSERT,
            ExecutionPlan.SqlSource.ANNOTATION,
            false,
            new ParameterBinder<?>[]{nullableBinder},
            null
        );

        new DefaultSqlEngine(provider(connection)).execute(plan);

        assertEquals(List.of("binder:1:null", "setNull:1:" + Types.VARCHAR, "executeUpdate"), events);
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

    private PreparedStatement statement(List<String> events) {
        return (PreparedStatement) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class[]{PreparedStatement.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "setNull" -> {
                    events.add("setNull:" + args[0] + ":" + args[1]);
                    yield null;
                }
                case "setObject" -> {
                    events.add("setObject:" + args[0] + ":" + args[1]);
                    yield null;
                }
                case "executeUpdate" -> {
                    events.add("executeUpdate");
                    yield 1;
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
