package org.liteorm.test.api;

import org.junit.jupiter.api.Test;
import org.liteorm.annotation.ExecutorRef;
import org.liteorm.annotation.UseDataSource;
import org.liteorm.api.DataSourceKeyProvider;
import org.liteorm.api.DataSourceRoutingException;
import org.liteorm.api.DataSourceSelection;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.SqlExecutor;
import org.liteorm.api.SqlExecutorRegistry;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiDataSourceContractTest {

    @Test
    void selectionContainsOnlyTypedRoutingInputAndStatementMetadata() {
        RouteInput input = new RouteInput("tenant-a", 7L);

        DataSourceSelection<RouteInput> selection = new DataSourceSelection<>(
            "example.OrderMapper.find",
            ExecutionPlan.StatementType.SELECT,
            input
        );

        assertEquals("example.OrderMapper.find", selection.statementId());
        assertEquals(ExecutionPlan.StatementType.SELECT, selection.statementType());
        assertSame(input, selection.parameter());
        assertThrows(IllegalArgumentException.class, () ->
            new DataSourceSelection<>(" ", ExecutionPlan.StatementType.SELECT, input));
    }

    @Test
    void providerIsAnOrdinaryTypedStrategy() {
        DataSourceKeyProvider<RouteInput> provider = selection ->
            selection.parameter().tenantId() + "-orders";

        String key = provider.select(new DataSourceSelection<>(
            "example.OrderMapper.find",
            ExecutionPlan.StatementType.SELECT,
            new RouteInput("tenant-a", 7L)
        ));

        assertEquals("tenant-a-orders", key);
    }

    @Test
    void registryPerformsOneReadOnlyNamedLookup() {
        SqlExecutor expected = plan -> null;
        SqlExecutorRegistry registry = (statementId, dataSourceKey) -> {
            assertEquals("example.OrderMapper.find", statementId);
            assertEquals("orders", dataSourceKey);
            return expected;
        };

        assertSame(expected, registry.require("example.OrderMapper.find", "orders"));
        assertEquals(1, SqlExecutorRegistry.class.getDeclaredMethods().length);
    }

    @Test
    void routingFailureContainsStatementAndKeyWithoutParameters() {
        DataSourceRoutingException failure = new DataSourceRoutingException(
            "example.OrderMapper.find",
            "tenant-a-orders",
            "No SqlExecutor is registered"
        );

        assertEquals("example.OrderMapper.find", failure.getStatementId());
        assertEquals("tenant-a-orders", failure.getDataSourceKey());
        assertTrue(failure.getMessage().contains("example.OrderMapper.find"));
        assertTrue(failure.getMessage().contains("tenant-a-orders"));
        assertFalse(failure.getMessage().contains("customer-secret"));
    }

    @Test
    void useDataSourceSupportsTypeAndMethodStaticOrProviderSelection() throws Exception {
        Target target = UseDataSource.class.getAnnotation(Target.class);
        Retention retention = UseDataSource.class.getAnnotation(Retention.class);
        Method value = UseDataSource.class.getMethod("value");
        Method provider = UseDataSource.class.getMethod("provider");

        assertEquals(Set.of(ElementType.TYPE, ElementType.METHOD), Set.copyOf(Arrays.asList(target.value())));
        assertEquals(RetentionPolicy.CLASS, retention.value());
        assertEquals("", value.getDefaultValue());
        assertEquals(DataSourceKeyProvider.None.class, provider.getDefaultValue());
    }

    @Test
    void executorRefIsRuntimeConstructorParameterMetadata() {
        Target target = ExecutorRef.class.getAnnotation(Target.class);
        Retention retention = ExecutorRef.class.getAnnotation(Retention.class);

        assertEquals(Set.of(ElementType.PARAMETER), Set.copyOf(Arrays.asList(target.value())));
        assertEquals(RetentionPolicy.RUNTIME, retention.value());
    }

    private record RouteInput(String tenantId, long orderId) {
    }
}
