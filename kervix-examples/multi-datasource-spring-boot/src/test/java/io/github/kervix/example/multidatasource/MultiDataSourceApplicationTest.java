package io.github.kervix.example.multidatasource;

import org.junit.jupiter.api.Test;
import io.github.kervix.example.multidatasource.order.OrderMapper;
import io.github.kervix.example.multidatasource.user.UserMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@SpringBootTest
class MultiDataSourceApplicationTest {

    @Test
    void registersMappersFromDisjointPackages(
            ApplicationContext context,
            @Qualifier("usersDataSource") DataSource usersDataSource,
            @Qualifier("ordersDataSource") DataSource ordersDataSource) {
        assertNotNull(context.getBean(UserMapper.class));
        assertNotNull(context.getBean(OrderMapper.class));
        assertSame(usersDataSource, context.getBean("usersDataSource", DriverManagerDataSource.class));
        assertSame(ordersDataSource, context.getBean("ordersDataSource", DriverManagerDataSource.class));
    }
}
