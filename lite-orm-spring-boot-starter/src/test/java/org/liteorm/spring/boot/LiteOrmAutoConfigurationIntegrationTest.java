package org.liteorm.spring.boot;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.liteorm.spring.boot.fixture.SpringUser;
import org.liteorm.spring.boot.fixture.SpringUserMapper;
import org.liteorm.api.SqlEngine;
import org.liteorm.api.ExecutionInterceptor;
import org.liteorm.api.ExecutionInvocation;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LiteOrmAutoConfigurationIntegrationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(LiteOrmAutoConfiguration.class))
        .withUserConfiguration(DatabaseConfiguration.class)
        .withPropertyValues("lite-orm.mapper-packages=org.liteorm.spring.boot.fixture");

    @Test
    void registersGeneratedMapperAndUsesApplicationDataSource() {
        contextRunner.run(context -> {
            SpringUserMapper mapper = context.getBean(SpringUserMapper.class);
            TrackingDataSource dataSource = context.getBean(TrackingDataSource.class);
            dataSource.resetCounts();

            assertSame(dataSource, context.getBean(SpringConnectionProvider.class).getDataSource());
            assertSame(context.getBean(SqlEngine.class), ReflectionTestUtils.getField(mapper, "sqlEngine"));
            assertEquals(1, mapper.insert(1L, "Alice"));
            assertEquals(new SpringUser(1L, "Alice"), mapper.findById(1L));
            assertEquals(2, dataSource.acquisitions());
            assertEquals(2, dataSource.releases());
        });
    }

    @Test
    void disablingStarterPreventsLiteOrmBeansAndMapperRegistration() {
        contextRunner.withPropertyValues("lite-orm.enabled=false").run(context -> {
            assertEquals(0, context.getBeansOfType(SpringUserMapper.class).size());
            assertEquals(0, context.getBeansOfType(SpringConnectionProvider.class).size());
        });
    }

    @Test
    void joinsSpringTransactionAndRollsBackWithIt() {
        contextRunner.run(context -> {
            SpringUserMapper mapper = context.getBean(SpringUserMapper.class);
            PlatformTransactionManager transactionManager = context.getBean(PlatformTransactionManager.class);
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);

            transaction.executeWithoutResult(status -> {
                mapper.insert(2L, "Bob");
                assertEquals(new SpringUser(2L, "Bob"), mapper.findById(2L));
                status.setRollbackOnly();
            });

            assertNull(mapper.findById(2L));
        });
    }

    @Test
    void springTransactionCommitsMultipleMapperCallsOnOnePhysicalConnection() {
        contextRunner.run(context -> {
            SpringUserMapper mapper = context.getBean(SpringUserMapper.class);
            PlatformTransactionManager transactionManager = context.getBean(PlatformTransactionManager.class);
            TrackingDataSource dataSource = context.getBean(TrackingDataSource.class);
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            dataSource.resetCounts();

            transaction.executeWithoutResult(status -> {
                mapper.insert(20L, "Dora");
                mapper.insert(21L, "Evan");
                assertEquals(new SpringUser(20L, "Dora"), mapper.findById(20L));
                assertEquals(new SpringUser(21L, "Evan"), mapper.findById(21L));
                assertEquals(1, dataSource.acquisitions());
            });

            assertEquals(new SpringUser(20L, "Dora"), mapper.findById(20L));
            assertEquals(new SpringUser(21L, "Evan"), mapper.findById(21L));
        });
    }

    @Test
    void collectsOrderedInterceptorsAndRollsBackWhenBeforeCallbackFails() {
        contextRunner.withUserConfiguration(InterceptorConfiguration.class).run(context -> {
            SpringUserMapper mapper = context.getBean(SpringUserMapper.class);
            PlatformTransactionManager transactionManager = context.getBean(PlatformTransactionManager.class);
            TransactionTemplate transaction = new TransactionTemplate(transactionManager);
            List<String> events = context.getBean("interceptorEvents", List.class);

            RuntimeException failure = assertThrows(RuntimeException.class, () -> transaction.executeWithoutResult(status -> {
                mapper.insert(3L, "Carol");
                mapper.insert(4L, "blocked");
            }));

            assertEquals("blocked by interceptor", failure.getCause().getMessage());
            assertEquals(List.of("first:Carol", "second:Carol", "first:blocked"), events);
            assertNull(mapper.findById(3L));
            assertNull(mapper.findById(4L));
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class DatabaseConfiguration {

        @Bean
        TrackingDataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:liteorm-spring;DB_CLOSE_DELAY=-1");
            TrackingDataSource trackingDataSource = new TrackingDataSource(dataSource);
            new JdbcTemplate(trackingDataSource).execute(
                "CREATE TABLE IF NOT EXISTS spring_users (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            return trackingDataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }
    }

    static final class TrackingDataSource extends DelegatingDataSource {

        private final AtomicInteger acquisitions = new AtomicInteger();
        private final AtomicInteger releases = new AtomicInteger();

        TrackingDataSource(DataSource targetDataSource) {
            super(targetDataSource);
        }

        @Override
        public Connection getConnection() throws SQLException {
            acquisitions.incrementAndGet();
            Connection connection = super.getConnection();
            return (Connection) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("close")) {
                        releases.incrementAndGet();
                    }
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                }
            );
        }

        int acquisitions() {
            return acquisitions.get();
        }

        int releases() {
            return releases.get();
        }

        void resetCounts() {
            acquisitions.set(0);
            releases.set(0);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InterceptorConfiguration {

        @Bean
        List<String> interceptorEvents() {
            return new ArrayList<>();
        }

        @Bean
        @Order(1)
        ExecutionInterceptor firstInterceptor(List<String> interceptorEvents) {
            return new ExecutionInterceptor() {
                @Override
                public void beforeExecution(ExecutionInvocation invocation) {
                    Object[] parameters = invocation.parameters();
                    if (invocation.statementType() == org.liteorm.api.ExecutionPlan.StatementType.INSERT) {
                        interceptorEvents.add("first:" + parameters[1]);
                        if ("blocked".equals(parameters[1])) {
                            throw new IllegalStateException("blocked by interceptor");
                        }
                    }
                }
            };
        }

        @Bean
        @Order(2)
        ExecutionInterceptor secondInterceptor(List<String> interceptorEvents) {
            return new ExecutionInterceptor() {
                @Override
                public void beforeExecution(ExecutionInvocation invocation) {
                    if (invocation.statementType() == org.liteorm.api.ExecutionPlan.StatementType.INSERT) {
                        interceptorEvents.add("second:" + invocation.parameters()[1]);
                    }
                }
            };
        }
    }
}
