package io.github.lynxus.example.multidatasource;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@SpringBootApplication
public class MultiDataSourceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MultiDataSourceApplication.class, args);
    }

    @Bean(name = "usersDataSource")
    @Primary
    DataSource usersDataSource() {
        return dataSource("jdbc:postgresql://localhost:5432/users");
    }

    @Bean(name = "ordersDataSource")
    DataSource ordersDataSource() {
        return dataSource("jdbc:postgresql://localhost:5432/orders");
    }

    private DataSource dataSource(String url) {
        return new DriverManagerDataSource(url, "example", "example");
    }
}
