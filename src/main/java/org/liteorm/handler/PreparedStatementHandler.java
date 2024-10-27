package org.liteorm.handler;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author 张庆波
 * @since 创建于 2024/10/27 11:23
 */
public class PreparedStatementHandler extends AbstractBaseHandler {
    @Override
    public void handle(ChainContext context) throws Exception {
        Connection connection = context.getConnection();
        String sql = context.getSql();
        Object[] params = context.getParams();

        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
            setParameters(preparedStatement, params);

            // 执行查询或更新操作
            if (sql.trim().toLowerCase().startsWith("select")) {
                ResultSet resultSet = preparedStatement.executeQuery();
                context.setResultSet(resultSet);
            } else {
                int updateCount = preparedStatement.executeUpdate();
                // 返回影响的行数
                context.setResult(updateCount);
            }

            // 继续下一个Handler
            if (getNext() != null) {
                getNext().handle(context);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // 设置参数到PreparedStatement
    private void setParameters(PreparedStatement preparedStatement, Object[] params) throws SQLException {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                preparedStatement.setObject(i + 1, params[i]);
            }
        }
    }


}