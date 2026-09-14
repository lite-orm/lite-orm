package io.github.kervix.spring.boot;

import io.github.kervix.JdbcAssembly;
import io.github.kervix.api.ExecutionInterceptor;
import io.github.kervix.api.SqlExecutor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ListableBeanFactory;

import javax.sql.DataSource;

/**
 * Assembles one Spring-aware JDBC executor for a named DataSource.
 */
final class SpringJdbcSqlExecutorFactoryBean
        implements FactoryBean<SqlExecutor>, BeanFactoryAware {

    private final DataSource dataSource;
    private ListableBeanFactory beanFactory;

    SpringJdbcSqlExecutorFactoryBean(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = (ListableBeanFactory) beanFactory;
    }

    @Override
    public SqlExecutor getObject() {
        return JdbcAssembly.sqlExecutor(
            new SpringConnectionHandleFactory(dataSource),
            beanFactory.getBeanProvider(ExecutionInterceptor.class).orderedStream().toList()
        );
    }

    @Override
    public Class<?> getObjectType() {
        return SqlExecutor.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
