package org.liteorm.spring.boot;

import org.liteorm.DefaultSqlEngine;
import org.liteorm.api.ConnectionProvider;
import org.liteorm.api.ExecutionInterceptor;
import org.liteorm.api.SqlEngine;
import org.liteorm.runtime.*;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.List;

/**
 * LiteORM自动配置类
 * 
 * @author lite-orm
 * @since 2024/11/15
 */
@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@ConditionalOnClass({SqlEngine.class, DataSource.class})
@ConditionalOnProperty(prefix = "lite-orm", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(LiteOrmProperties.class)
public class LiteOrmAutoConfiguration {
    
    private final LiteOrmProperties properties;
    
    public LiteOrmAutoConfiguration(LiteOrmProperties properties) {
        this.properties = properties;
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ConnectionProvider liteOrmConnectionProvider(DataSource dataSource) {
        return new SpringConnectionProvider(dataSource);
    }

    @Bean
    public static GeneratedMapperBeanDefinitionRegistrar generatedMapperBeanDefinitionRegistrar() {
        return new GeneratedMapperBeanDefinitionRegistrar();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public SqlEngine liteOrmSqlEngine(
            ConnectionProvider connectionProvider,
            ObjectProvider<ExecutionInterceptor> interceptorProvider) {
        // 构建处理器链
        List<SqlProcessor> processors = new ArrayList<>();
        
        // 1. 日志处理器（如果启用）
        if (properties.isSqlLogging()) {
            processors.add(new LoggingProcessor(properties.isLogParameters(), true));
        }
        
        // 2. 慢查询监控
        if (properties.isSlowQueryMonitoring()) {
            processors.add(new SlowQueryMonitorProcessor(properties.getSlowQueryThreshold()));
        }
        
        // 3. 审计处理器（如果启用）
        if (properties.isAuditEnabled()) {
            processors.add(new SqlAuditProcessor(properties.isAuditAsync()));
        }
        
        // 4. 参数处理器
        processors.add(new ParameterProcessor());
        
        // 5. 执行处理器
        processors.add(new ExecutionProcessor());
        
        // 6. 结果处理器
        processors.add(new ResultProcessor());
        
        return new DefaultSqlEngine(
            connectionProvider,
            processors,
            interceptorProvider.orderedStream().toList()
        );
    }
}
