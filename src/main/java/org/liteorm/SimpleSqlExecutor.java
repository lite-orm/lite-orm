package org.liteorm;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 简单的SQL执行器实现
 * 只负责纯粹的JDBC操作，不包含任何业务逻辑
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class SimpleSqlExecutor implements SqlExecutor {

    @Override
    public List<Object[]> executeQuery(Connection connection, String sql, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setParameters(statement, params);
            
            try (ResultSet resultSet = statement.executeQuery()) {
                return extractResultSet(resultSet);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute query: " + sql, e);
        }
    }

    @Override
    public int executeUpdate(Connection connection, String sql, Object... params) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            setParameters(statement, params);
            return statement.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute update: " + sql, e);
        }
    }

    @Override
    public int[] executeBatch(Connection connection, String sql, List<Object[]> batchParams) {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (Object[] params : batchParams) {
                setParameters(statement, params);
                statement.addBatch();
            }
            return statement.executeBatch();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute batch: " + sql, e);
        }
    }

    /**
     * 设置PreparedStatement参数
     */
    private void setParameters(PreparedStatement statement, Object[] params) throws SQLException {
        if (params != null) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
        }
    }

    /**
     * 提取ResultSet为原始数据
     * 返回List<Object[]>，每个Object[]代表一行数据
     */
    private List<Object[]> extractResultSet(ResultSet resultSet) throws SQLException {
        List<Object[]> results = new ArrayList<>();
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();

        while (resultSet.next()) {
            Object[] row = new Object[columnCount];
            for (int i = 0; i < columnCount; i++) {
                row[i] = resultSet.getObject(i + 1);
            }
            results.add(row);
        }

        return results;
    }
}
