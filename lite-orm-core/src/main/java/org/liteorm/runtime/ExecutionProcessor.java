package org.liteorm.runtime;

import org.liteorm.ExecutionContext;
import org.liteorm.api.ExecutionPlan;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 执行处理器
 * 职责：执行SQL语句
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class ExecutionProcessor implements SqlProcessor {
    
    @Override
    public void process(ExecutionPlan plan, ExecutionContext context) {
        PreparedStatement statement = context.getPreparedStatement();
        if (statement == null) {
            throw new IllegalStateException("PreparedStatement is null");
        }
        
        try {
            switch (plan.getStatementType()) {
                case SELECT:
                    ResultSet resultSet = statement.executeQuery();
                    context.setResultSet(resultSet);
                    break;
                    
                case INSERT:
                case UPDATE:
                case DELETE:
                    int updateCount = statement.executeUpdate();
                    context.setUpdateCount(updateCount);
                    break;
                    
                default:
                    throw new UnsupportedOperationException("Unsupported SQL type: " + plan.getStatementType());
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute SQL: " + plan.getSql(), e);
        }
    }
}
