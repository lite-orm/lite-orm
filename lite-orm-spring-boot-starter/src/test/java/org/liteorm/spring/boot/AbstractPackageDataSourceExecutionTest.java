package org.liteorm.spring.boot;

import org.junit.jupiter.api.Test;
import org.liteorm.api.SqlExecutionException;
import org.liteorm.api.TransactionException;
import org.liteorm.spring.boot.archivefixture.ArchiveUserMapper;
import org.liteorm.spring.boot.fixture.SpringUserMapper;
import org.liteorm.testsupport.database.DatabaseEngine;
import org.liteorm.testsupport.database.TestDatabase;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

abstract class AbstractPackageDataSourceExecutionTest {

    protected abstract DatabaseEngine databaseEngine();

    @Test
    void bindsDisjointMapperPackagesToNamedExecutors() {
        contextRunner().withPropertyValues(bindings(
            "org.liteorm.spring.boot.fixture", "usersDataSource",
            "org.liteorm.spring.boot.archivefixture", "ordersDataSource"))
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
            "org.liteorm.spring.boot.fixture", "usersDataSource",
            "org.liteorm.spring.boot.archivefixture", "ordersDataSource"))
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
            "org.liteorm.spring.boot.fixture", "usersDataSource",
            "org.liteorm.spring.boot.archivefixture", "ordersDataSource"))
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
            "org.liteorm.spring.boot.fixture", "userRoutingDataSource"))
            .run(context -> {
                SpringUserMapper mapper = context.getBean("springUserMapper", SpringUserMapper.class);
                mapper.insert(3L, "routed");
                assertEquals("routed", mapper.findById(3L).name());
            });
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LiteOrmAutoConfiguration.class))
            .withUserConfiguration(DatabaseConfiguration.class)
            .withPropertyValues("liteorm.test-database-engine=" + databaseEngine());
    }

    private String[] bindings(String... values) {
        String[] properties = new String[values.length];
        for (int index = 0; index < values.length; index += 2) {
            int bindingIndex = index / 2;
            properties[index] = "lite-orm.mapper-bindings[" + bindingIndex + "].package-name=" + values[index];
            properties[index + 1] = "lite-orm.mapper-bindings[" + bindingIndex
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
        PlatformTransactionManager usersTransactionManager(
                @Qualifier("usersDataSource") DataSource usersDataSource) {
            return new DataSourceTransactionManager(usersDataSource);
        }

        @Bean
        PlatformTransactionManager ordersTransactionManager(
                @Qualifier("ordersDataSource") DataSource ordersDataSource) {
            return new DataSourceTransactionManager(ordersDataSource);
        }

        private DataSource dataSource(Environment environment) throws SQLException {
            DatabaseEngine engine = DatabaseEngine.valueOf(
                environment.getRequiredProperty("liteorm.test-database-engine"));
            TestDatabase database = TestDatabase.shared(engine);
            DataSource dataSource = database.createDataSource();
            database.execute(dataSource,
                "CREATE TABLE spring_users (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            return dataSource;
        }
    }
}
