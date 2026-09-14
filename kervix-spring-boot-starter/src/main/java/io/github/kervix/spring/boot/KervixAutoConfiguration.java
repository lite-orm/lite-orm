package io.github.kervix.spring.boot;

import io.github.kervix.api.SqlExecutor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * Kervix Spring Boot auto-configuration.
 * 
 * @author kervix
 * @since 2024/11/15
 */
@AutoConfiguration(afterName = {
    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration",
    "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration"
})
@ConditionalOnClass({SqlExecutor.class, DataSource.class})
@ConditionalOnProperty(prefix = "kervix", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(KervixProperties.class)
public class KervixAutoConfiguration {

    @Bean
    public static GeneratedMapperBeanDefinitionRegistrar generatedMapperBeanDefinitionRegistrar() {
        return new GeneratedMapperBeanDefinitionRegistrar();
    }

}
