package org.liteorm.handler;

import com.fizzed.rocker.Rocker;
import com.mysql.cj.jdbc.MysqlDataSource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;


import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author 王洋洋
 * @since 创建于 2024/11/02 20:41
 */
@Slf4j
public class RockerTest {
    public static final String url = "jdbc:mysql://localhost:3306/test?characterEncoding=utf8&serverTimezone=GMT%2B8";
    public static final String query = "SELECT id, name, email FROM users WHERE id = ?";
    public static final String query2 = "SELECT count(*) cnt FROM users";

    @Test
    public void testRockerParse() throws Exception {

        // dynamic interfaces, dynamic implementation
        String rendered = Rocker.template("template/User.rocker.raw")
                .bind("setId", 1)
                .render()
                .toString();
        log.info("dynamic sql parse {}", rendered);
    }


    @Test
    public void testJdbc() {
        try (Connection connection = createConnection();
             PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setInt(1, 1);
            ResultSet resultSet = preparedStatement.executeQuery();
            while (resultSet.next()) {
                log.info("{},{},{}", resultSet.getInt("id"), resultSet.getString("name"), resultSet.getString("email"));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static DataSource getMySQLDataSource() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(url);
        dataSource.setUser("root");
        dataSource.setPassword("123456");
        return dataSource;
    }

    private static Connection createConnection() throws SQLException {
        return getMySQLDataSource().getConnection();
    }
}