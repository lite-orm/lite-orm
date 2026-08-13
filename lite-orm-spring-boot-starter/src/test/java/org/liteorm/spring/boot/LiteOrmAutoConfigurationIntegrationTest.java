package org.liteorm.spring.boot;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.liteorm.spring.boot.fixture.SpringUser;
import org.liteorm.spring.boot.fixture.SpringUserMapper;
import org.liteorm.api.SqlEngine;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

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
}
