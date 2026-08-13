package org.liteorm.runtime;

import org.liteorm.ExecutionContext;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.RowMapper;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 结果处理器
 * 职责：提取ResultSet为原始数据
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class ResultProcessor implements SqlProcessor {
    
    @Override
    public void process(ExecutionPlan plan, ExecutionContext context) {
        if (plan.getStatementType() == ExecutionPlan.StatementType.SELECT) {
            // 处理查询结果
            ResultSet resultSet = context.getResultSet();
            if (resultSet != null) {
                try {
                    List<Object[]> results = plan.getRowMapper() == null
                        ? extractResultSet(resultSet)
                        : mapResultSet(resultSet, plan.getRowMapper());
                    context.setQueryResults(results);
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to extract ResultSet", e);
                }
            }
        }
        // 更新操作的结果已在ExecutionProcessor中设置
    }

    private List<Object[]> mapResultSet(ResultSet resultSet, RowMapper<?> rowMapper) throws SQLException {
        List<Object[]> results = new ArrayList<>();
        while (resultSet.next()) {
            results.add(new Object[]{rowMapper.map(resultSet)});
        }
        return results;
    }
    
    /**
     * 提取ResultSet为Object[]列表
     */
    private List<Object[]> extractResultSet(ResultSet resultSet) throws SQLException {
        List<Object[]> results = new ArrayList<>();
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        while (resultSet.next()) {
            Object[] row = new Object[columnCount];
            for (int i = 1; i <= columnCount; i++) {
                row[i - 1] = resultSet.getObject(i);
            }
            results.add(row);
        }
        
        return results;
    }
}
