package io.github.kervix.spring.boot;

import org.junit.jupiter.api.Test;
import io.github.kervix.api.SqlExecutionException;
import io.github.kervix.api.TransactionException;
import io.github.kervix.spring.boot.archivefixture.ArchiveUserMapper;
import io.github.kervix.spring.boot.fixture.SpringUserMapper;
import io.github.kervix.testsupport.database.DatabaseEngine;
import io.github.kervix.testsupport.database.TestDatabase;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

abstract class AbstractPackageDataSourceExecutionTest {

    protected abstract DatabaseEngine databaseEngine();

    @Test
    void bindsDisjointMapperPackagesToNamedExecutors() {
        contextRunner().withPropertyValues(bindings(
            "io.github.kervix.spring.boot.fixture", "usersDataSource",
            "io.github.kervix.spring.boot.archivefixture", "ordersDataSource"))
            .run(context -> {
                SpringUserMapper users = context.getBean("springUserMapper", SpringUserMapper.class);
                ArchiveUserMapper orders = context.getBean("archiveUserMapper", ArchiveUserMapper.class);

                users.insert(1L, "users");
                orders.insert(1L, "orders");

                assertEquals("users", users.findById(1L).name());
                assertEquals("orders", orders.findById(1L).name());
            });
    }

    @Test
    void matchingTransactionManagerControlsOnlyItsExecutor() {
        contextRunner().withPropertyValues(bindings(
            "io.github.kervix.spring.boot.fixture", "usersDataSource",
            "io.github.kervix.spring.boot.archivefixture", "ordersDataSource"))
            .run(context -> {
                SpringUserMapper users = context.getBean("springUserMapper", SpringUserMapper.class);
                ArchiveUserMapper orders = context.getBean("archiveUserMapper", ArchiveUserMapper.class);
                TransactionTemplate usersTransactions = new TransactionTemplate(
                    context.getBean("usersTransactionManager", PlatformTransactionManager.class));
                TransactionTemplate ordersTransactions = new TransactionTemplate(
                    context.getBean("ordersTransactionManager", PlatformTransactionManager.class));

                usersTransactions.executeWithoutResult(status -> {
                    users.insert(2L, "users-rollback");
                    status.setRollbackOnly();
                });
                ordersTransactions.executeWithoutResult(status -> orders.insert(2L, "orders-commit"));

                assertNull(users.findById(2L));
                assertEquals("orders-commit", orders.findById(2L).name());
            });
    }

    @Test
    void rejectsMapperBoundToAnotherDataSourceInsideActiveTransaction() {
        contextRunner().withPropertyValues(bindings(
            "io.github.kervix.spring.boot.fixture", "usersDataSource",
            "io.github.kervix.spring.boot.archivefixture", "ordersDataSource"))
            .run(context -> {
                ArchiveUserMapper orders = context.getBean("archiveUserMapper", ArchiveUserMapper.class);
                TransactionTemplate usersTransactions = new TransactionTemplate(
                    context.getBean("usersTransactionManager", PlatformTransactionManager.class));

                SqlExecutionException failure = assertThrows(SqlExecutionException.class, () ->
                    usersTransactions.executeWithoutResult(status -> orders.insert(3L, "must-not-auto-commit")));

                TransactionException cause = (TransactionException) failure.getCause();
                assertEquals(TransactionException.Type.DOMAIN_MISMATCH, cause.getType());
                assertNull(orders.findById(3L));
            });
    }

    @Test
    void acceptsRoutingDataSourceAsTheConfiguredDataSource() {
        contextRunner().withPropertyValues(bindings(
            "io.github.kervix.spring.boot.fixture", "userRoutingDataSource"))
            .run(context -> {
                SpringUserMapper mapper = context.getBean("springUserMapper", SpringUserMapper.class);
                mapper.insert(3L, "routed");
                assertEquals("routed", mapper.findById(3L).name());
            });
    }

    @Test
    void routesReadAndWriteOperationsThroughOneConfiguredDataSource() {
        contextRunner().withPropertyValues(bindings(
            "io.github.kervix.spring.boot.fixture", "readWriteDataSource"))
            .run(context -> {
                SpringUserMapper mapper = context.getBean("springUserMapper", SpringUserMapper.class);
                RecordingRoutingDataSource routingDataSource =
                    context.getBean("readWriteDataSource", RecordingRoutingDataSource.class);

                RouteContext.set("write");
                try {
                    mapper.insert(4L, "routed-write");
                    RouteContext.set("read");
                    assertEquals("routed-write", mapper.findById(4L).name());
                } finally {
                    RouteContext.clear();
                }

                assertEquals(List.of("write", "read"), routingDataSource.routes());
            });
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KervixAutoConfiguration.class))
            .withUserConfiguration(DatabaseConfiguration.class)
            .withPropertyValues("kervix.test-database-engine=" + databaseEngine());
    }

    private String[] bindings(String... values) {
        String[] properties = new String[values.length];
        for (int index = 0; index < values.length; index += 2) {
            int bindingIndex = index / 2;
            properties[index] = "kervix.mapper-bindings[" + bindingIndex + "].package-name=" + values[index];
            properties[index + 1] = "kervix.mapper-bindings[" + bindingIndex
                + "].data-source=" + values[index + 1];
        }
        return properties;
    }

    @Configuration(proxyBeanMethods = false)
    static class DatabaseConfiguration {

        @Bean
        DataSource usersDataSource(Environment environment) throws SQLException {
            return dataSource(environment);
        }

        @Bean
        DataSource ordersDataSource(Environment environment) throws SQLException {
            return dataSource(environment);
        }

        @Bean
        DataSource userRoutingDataSource(@Qualifier("usersDataSource") DataSource usersDataSource) {
            AbstractRoutingDataSource routingDataSource = new AbstractRoutingDataSource() {
                @Override
                protected Object determineCurrentLookupKey() {
                    return "users";
                }
            };
            routingDataSource.setDefaultTargetDataSource(usersDataSource);
            routingDataSource.setTargetDataSources(java.util.Map.of("users", usersDataSource));
            return routingDataSource;
        }

        @Bean
        RecordingRoutingDataSource readWriteDataSource(
                @Qualifier("usersDataSource") DataSource usersDataSource) {
            RecordingRoutingDataSource routingDataSource = new RecordingRoutingDataSource();
            routingDataSource.setDefaultTargetDataSource(usersDataSource);
            routingDataSource.setTargetDataSources(java.util.Map.of(
                "read", usersDataSource,
                "write", usersDataSource));
            return routingDataSource;
        }

        @Bean
        PlatformTransactionManager usersTransactionManager(
                @Qualifier("usersDataSource") DataSource usersDataSource) {
            return new DataSourceTransactionManager(usersDataSource);
        }

        @Bean
        PlatformTransactionManager ordersTransactionManager(
                @Qualifier("ordersDataSource") DataSource ordersDataSource) {
            return new DataSourceTransactionManager(ordersDataSource);
        }

        @Bean
        PlatformTransactionManager readWriteTransactionManager(
                @Qualifier("readWriteDataSource") DataSource readWriteDataSource) {
            return new DataSourceTransactionManager(readWriteDataSource);
        }

        private DataSource dataSource(Environment environment) throws SQLException {
            DatabaseEngine engine = DatabaseEngine.valueOf(
                environment.getRequiredProperty("kervix.test-database-engine"));
            TestDatabase database = TestDatabase.shared(engine);
            DataSource dataSource = database.createDataSource();
            database.execute(dataSource,
                "CREATE TABLE spring_users (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            return dataSource;
        }
    }

    private static final class RouteContext {

        private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

        private RouteContext() {
        }

        static void set(String route) {
            CURRENT.set(route);
        }

        static String current() {
            return CURRENT.get();
        }

        static void clear() {
            CURRENT.remove();
        }
    }

    static final class RecordingRoutingDataSource extends AbstractRoutingDataSource {

        private final List<String> selectedRoutes = Collections.synchronizedList(new ArrayList<>());

        @Override
        protected Object determineCurrentLookupKey() {
            String route = RouteContext.current();
            selectedRoutes.add(route);
            return route;
        }

        List<String> routes() {
            return List.copyOf(selectedRoutes);
        }
    }
}
