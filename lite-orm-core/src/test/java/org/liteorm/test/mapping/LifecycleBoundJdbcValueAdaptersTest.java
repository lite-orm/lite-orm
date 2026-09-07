package org.liteorm.test.mapping;

import org.junit.jupiter.api.Test;
import org.liteorm.jdbc.StandardJdbcTypeMappings;
import org.liteorm.runtime.ResultValueConverters;

import java.lang.reflect.Proxy;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.NClob;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleBoundJdbcValueAdaptersTest {

    @Test
    void preservesNullWithoutCreatingDriverResources() throws Exception {
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> switch (method.getName()) {
            case "getBlob", "getClob", "getNClob", "getSQLXML", "getArray" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });

        assertNull(new StandardJdbcTypeMappings.BlobJdbcValueAdapter().getNullable(resultSet, 1));
        assertNull(new StandardJdbcTypeMappings.ClobJdbcValueAdapter().getNullable(resultSet, 1));
        assertNull(new StandardJdbcTypeMappings.NClobJdbcValueAdapter().getNullable(resultSet, 1));
        assertNull(new StandardJdbcTypeMappings.SqlXmlJdbcValueAdapter().getNullable(resultSet, 1));
        assertNull(ResultValueConverters.materializeJdbcArray(resultSet, 1));
    }

    @Test
    void materializesBlobBeforeReleasingDriverResource() throws Exception {
        AtomicBoolean freed = new AtomicBoolean();
        byte[] contents = {1, 2, 3, 4};
        Blob blob = proxy(Blob.class, (method, arguments) -> switch (method.getName()) {
            case "length" -> (long) contents.length;
            case "getBytes" -> contents.clone();
            case "free" -> {
                freed.set(true);
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> {
            if (method.getName().equals("getBlob")) {
                return blob;
            }
            throw new UnsupportedOperationException(method.getName());
        });

        byte[] materialized = new StandardJdbcTypeMappings.BlobJdbcValueAdapter()
            .getNullable(resultSet, 1);

        assertArrayEquals(contents, materialized);
        assertTrue(freed.get());
    }

    @Test
    void preservesBlobReadFailureAndSuppressesReleaseFailure() {
        SQLException readFailure = new SQLException("read");
        SQLException releaseFailure = new SQLException("free");
        Blob blob = proxy(Blob.class, (method, arguments) -> switch (method.getName()) {
            case "length" -> 4L;
            case "getBytes" -> throw readFailure;
            case "free" -> throw releaseFailure;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> {
            if (method.getName().equals("getBlob")) {
                return blob;
            }
            throw new UnsupportedOperationException(method.getName());
        });

        SQLException thrown = assertThrows(SQLException.class, () ->
            new StandardJdbcTypeMappings.BlobJdbcValueAdapter().getNullable(resultSet, 1));

        assertSame(readFailure, thrown);
        assertEquals(1, thrown.getSuppressed().length);
        assertSame(releaseFailure, thrown.getSuppressed()[0]);
    }

    @Test
    void materializesClobBeforeReleasingDriverResource() throws Exception {
        AtomicBoolean freed = new AtomicBoolean();
        String contents = "large text value";
        Clob clob = proxy(Clob.class, (method, arguments) -> switch (method.getName()) {
            case "length" -> (long) contents.length();
            case "getSubString" -> contents;
            case "free" -> {
                freed.set(true);
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> {
            if (method.getName().equals("getClob")) {
                return clob;
            }
            throw new UnsupportedOperationException(method.getName());
        });

        String materialized = new StandardJdbcTypeMappings.ClobJdbcValueAdapter()
            .getNullable(resultSet, 1);

        assertEquals(contents, materialized);
        assertTrue(freed.get());
    }

    @Test
    void materializesNClobBeforeReleasingDriverResource() throws Exception {
        AtomicBoolean freed = new AtomicBoolean();
        String contents = "national text value";
        NClob nclob = proxy(NClob.class, (method, arguments) -> switch (method.getName()) {
            case "length" -> (long) contents.length();
            case "getSubString" -> contents;
            case "free" -> {
                freed.set(true);
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> {
            if (method.getName().equals("getNClob")) {
                return nclob;
            }
            throw new UnsupportedOperationException(method.getName());
        });

        String materialized = new StandardJdbcTypeMappings.NClobJdbcValueAdapter()
            .getNullable(resultSet, 1);

        assertEquals(contents, materialized);
        assertTrue(freed.get());
    }

    @Test
    void materializesSqlXmlBeforeReleasingDriverResource() throws Exception {
        AtomicBoolean freed = new AtomicBoolean();
        String contents = "<value>xml</value>";
        SQLXML sqlxml = proxy(SQLXML.class, (method, arguments) -> switch (method.getName()) {
            case "getString" -> contents;
            case "free" -> {
                freed.set(true);
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> {
            if (method.getName().equals("getSQLXML")) {
                return sqlxml;
            }
            throw new UnsupportedOperationException(method.getName());
        });

        String materialized = new StandardJdbcTypeMappings.SqlXmlJdbcValueAdapter()
            .getNullable(resultSet, 1);

        assertEquals(contents, materialized);
        assertTrue(freed.get());
    }

    @Test
    void materializesJdbcArrayBeforeReleasingDriverResource() throws Exception {
        AtomicBoolean freed = new AtomicBoolean();
        String[] contents = {"one", "two"};
        Array array = proxy(Array.class, (method, arguments) -> switch (method.getName()) {
            case "getArray" -> contents.clone();
            case "free" -> {
                freed.set(true);
                yield null;
            }
            default -> throw new UnsupportedOperationException(method.getName());
        });
        ResultSet resultSet = proxy(ResultSet.class, (method, arguments) -> {
            if (method.getName().equals("getArray")) {
                return array;
            }
            throw new UnsupportedOperationException(method.getName());
        });

        Object[] materialized = ResultValueConverters.materializeJdbcArray(resultSet, 1);

        assertArrayEquals(contents, materialized);
        assertTrue(freed.get());
    }

    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return type.cast(Proxy.newProxyInstance(
            type.getClassLoader(),
            new Class<?>[]{type},
            (proxy, method, arguments) -> invocation.invoke(method, arguments)));
    }

    @FunctionalInterface
    private interface Invocation {
        Object invoke(java.lang.reflect.Method method, Object[] arguments) throws Throwable;
    }
}
