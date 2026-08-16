package org.liteorm.spring.boot;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.liteorm.spring.boot.fixture.SpringUserMapper;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackageDataSourceBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(LiteOrmAutoConfiguration.class))
        .withUserConfiguration(MultipleDataSourceConfiguration.class);

    @Test
    void bindsSameGeneratedMapperToTwoNamedExecutors() {
        contextRunner
            .withPropertyValues(bindings(
                "org.liteorm.spring.boot.fixture", "usersDataSource", "users",
                "org.liteorm.spring.boot.fixture", "ordersDataSource", "orders"))
            .run(context -> {
                assertNull(context.getStartupFailure());
                SpringUserMapper users = context.getBean("usersSpringUserMapper", SpringUserMapper.class);
                SpringUserMapper orders = context.getBean("ordersSpringUserMapper", SpringUserMapper.class);

                users.insert(1L, "users");
                orders.insert(1L, "orders");

                assertEquals("users", users.findById(1L).name());
                assertEquals("orders", orders.findById(1L).name());
            });
    }

    @Test
    void matchingTransactionManagerControlsOnlyItsExecutor() {
        contextRunner
            .withPropertyValues(bindings(
                "org.liteorm.spring.boot.fixture", "usersDataSource", "users",
                "org.liteorm.spring.boot.fixture", "ordersDataSource", "orders"))
            .run(context -> {
                SpringUserMapper users = context.getBean("usersSpringUserMapper", SpringUserMapper.class);
                SpringUserMapper orders = context.getBean("ordersSpringUserMapper", SpringUserMapper.class);
                TransactionTemplate usersTransactions = new TransactionTemplate(
                    context.getBean("usersTransactionManager", PlatformTransactionManager.class));
                TransactionTemplate ordersTransactions = new TransactionTemplate(
                    context.getBean("ordersTransactionManager", PlatformTransactionManager.class));

                usersTransactions.executeWithoutResult(status -> {
                    users.insert(2L, "users-rollback");
                    status.setRollbackOnly();
                });
                ordersTransactions.executeWithoutResult(status ->
                    orders.insert(2L, "orders-commit"));

                assertNull(users.findById(2L));
                assertEquals("orders-commit", orders.findById(2L).name());
            });
    }

    @Test
    void failsForMissingExecutorBean() {
        contextRunner
            .withPropertyValues(bindings(
                "org.liteorm.spring.boot.fixture", "missingDataSource", "missing"))
            .run(context -> assertStartupFailureContains(context.getStartupFailure(), "missingDataSource"));
    }

    @Test
    void failsForOverlappingPackageRules() {
        contextRunner
            .withPropertyValues(bindings(
                "org.liteorm.spring.boot", "usersDataSource", "all",
                "org.liteorm.spring.boot.fixture", "ordersDataSource", "fixture"))
            .run(context -> assertStartupFailureContains(context.getStartupFailure(), "overlap"));
    }

    @Test
    void failsForDuplicateMapperBeanNames() {
        contextRunner
            .withPropertyValues(bindings(
                "org.liteorm.spring.boot.fixture", "usersDataSource", "",
                "org.liteorm.spring.boot.fixture", "ordersDataSource", ""))
            .run(context -> assertStartupFailureContains(context.getStartupFailure(), "Duplicate"));
    }

    @Test
    void requiresDataSourceNameInsteadOfChoosingAmongMultipleBeans() {
        contextRunner
            .withPropertyValues(bindings(
                "org.liteorm.spring.boot.fixture", "", "users"))
            .run(context -> assertStartupFailureContains(context.getStartupFailure(), "data-source"));
    }

    @Test
    void acceptsRoutingDataSourceAsTheConfiguredDataSource() {
        contextRunner
            .withPropertyValues(bindings(
                "org.liteorm.spring.boot.fixture", "userRoutingDataSource", "routed"))
            .run(context -> {
                SpringUserMapper mapper = context.getBean(
                    "routedSpringUserMapper", SpringUserMapper.class);

                mapper.insert(3L, "routed");

                assertEquals("routed", mapper.findById(3L).name());
            });
    }

    private String[] bindings(String... values) {
        String[] properties = new String[values.length];
        for (int index = 0; index < values.length; index += 3) {
            int bindingIndex = index / 3;
            properties[index] = "lite-orm.mapper-bindings[" + bindingIndex + "].package-name=" + values[index];
            properties[index + 1] = "lite-orm.mapper-bindings[" + bindingIndex
                + "].data-source=" + values[index + 1];
            properties[index + 2] = "lite-orm.mapper-bindings[" + bindingIndex
                + "].bean-name-prefix=" + values[index + 2];
        }
        return properties;
    }

    private void assertStartupFailureContains(Throwable failure, String expected) {
        assertNotNull(failure);
        Throwable current = failure;
        while (current != null) {
            if (current.getMessage() != null && current.getMessage().contains(expected)) {
                return;
            }
            current = current.getCause();
        }
        assertTrue(false, "Expected startup failure containing: " + expected);
    }

    @Configuration(proxyBeanMethods = false)
    static class MultipleDataSourceConfiguration {

        @Bean
        DataSource usersDataSource() throws SQLException {
            return dataSource("users");
        }

        @Bean
        DataSource ordersDataSource() throws SQLException {
            return dataSource("orders");
        }

        @Bean
        DataSource userRoutingDataSource(
                @Qualifier("usersDataSource") DataSource usersDataSource) {
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

        private DataSource dataSource(String name) throws SQLException {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:" + name + '-' + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
            try (var connection = dataSource.getConnection();
                 var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE spring_users (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            }
            return dataSource;
        }
    }
}
