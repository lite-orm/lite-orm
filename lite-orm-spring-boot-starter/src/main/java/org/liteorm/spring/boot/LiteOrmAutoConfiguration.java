package org.liteorm.spring.boot;

import org.liteorm.api.ExecutionInterceptor;
import org.liteorm.api.SqlExecutor;
import org.liteorm.jdbc.JdbcSqlExecutor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * LiteORM自动配置类
 * 
 * @author lite-orm
 * @since 2024/11/15
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass({SqlExecutor.class, DataSource.class})
@ConditionalOnProperty(prefix = "lite-orm", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LiteOrmProperties.class)
public class LiteOrmAutoConfiguration {

    @Bean
    public static GeneratedMapperBeanDefinitionRegistrar generatedMapperBeanDefinitionRegistrar() {
        return new GeneratedMapperBeanDefinitionRegistrar();
    }

    @Bean
    @ConditionalOnMissingBean(SqlExecutor.class)
    public SqlExecutor sqlExecutor(
            DataSource dataSource,
            ObjectProvider<ExecutionInterceptor> interceptors) {
        return new JdbcSqlExecutor(
            new SpringTransactionFactory(dataSource),
            interceptors.orderedStream().toList()
        );
    }
}
