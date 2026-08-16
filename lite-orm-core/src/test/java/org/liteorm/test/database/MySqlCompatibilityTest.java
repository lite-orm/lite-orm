package org.liteorm.test.database;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;

@Testcontainers(disabledWithoutDocker = true)
class MySqlCompatibilityTest extends AbstractDatabaseCompatibilityTest {

    @Container
    private static final MySQLContainer<?> DATABASE = new MySQLContainer<>(
        DockerImageName.parse("mysql:8.4.0"));

    @Override
    protected DataSource dataSource() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setURL(DATABASE.getJdbcUrl());
        dataSource.setUser(DATABASE.getUsername());
        dataSource.setPassword(DATABASE.getPassword());
        return dataSource;
    }

    @Override
    protected String identityDefinition() {
        return "BIGINT AUTO_INCREMENT PRIMARY KEY";
    }

    @Override
    protected String binaryDefinition() {
        return "VARBINARY(255)";
    }

    @Override
    protected String sleepSql() {
        return "SELECT SLEEP(3)";
    }
}
