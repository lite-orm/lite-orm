package org.liteorm.handler;

import com.mysql.cj.jdbc.MysqlDataSource;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * @author 张庆波
 * @since 创建于 2024/10/27 20:41
 */
@Slf4j
public class HandlerChainTest {
    public static final String url = "jdbc:mysql://192.168.0.12:3306/test?characterEncoding=utf8&serverTimezone=GMT%2B8";
    public static final String query = "SELECT id, name, email FROM users WHERE id = ?";

    @Test
    public void testChain() throws Exception {
        ConnectionHandler connectionHandler = new ConnectionHandler();
        PreparedStatementHandler statementHandler = new PreparedStatementHandler();
        ResultHandler resultHandler = new ResultHandler();
        TransactionHandler transactionHandler = new TransactionHandler();
        HandlerChain chain = new HandlerChain(connectionHandler, transactionHandler, statementHandler, resultHandler);

        ChainContext<User> chainContext = new ChainContext<>(User.class);
        chainContext.setDataSource(getMySQLDataSource());
        chainContext.setSql(query);
        chainContext.setParams(new Object[]{1});

        List<User> users = chain.execute(chainContext);
        log.info("{}", users);
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