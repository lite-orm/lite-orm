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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

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
            DataSource dataSource = context.getBean(DataSource.class);

            assertSame(dataSource, context.getBean(LiteOrmConnectionManager.class).getDataSource());
            assertSame(context.getBean(SqlEngine.class), ReflectionTestUtils.getField(mapper, "sqlEngine"));
            assertEquals(1, mapper.insert(1L, "Alice"));
            assertEquals(new SpringUser(1L, "Alice"), mapper.findById(1L));
        });
    }

    @Test
    void disablingStarterPreventsLiteOrmBeansAndMapperRegistration() {
        contextRunner.withPropertyValues("lite-orm.enabled=false").run(context -> {
            assertEquals(0, context.getBeansOfType(SpringUserMapper.class).size());
            assertEquals(0, context.getBeansOfType(LiteOrmConnectionManager.class).size());
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
        DataSource dataSource() {
            JdbcDataSource dataSource = new JdbcDataSource();
            dataSource.setURL("jdbc:h2:mem:liteorm-spring;DB_CLOSE_DELAY=-1");
            new JdbcTemplate(dataSource).execute(
                "CREATE TABLE IF NOT EXISTS spring_users (id BIGINT PRIMARY KEY, name VARCHAR(100))");
            return dataSource;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
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
