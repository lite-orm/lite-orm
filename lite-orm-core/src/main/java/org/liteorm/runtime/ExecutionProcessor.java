package org.liteorm.runtime;

import org.liteorm.ExecutionContext;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.BatchExecutionPlan;

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
                    if (plan.returnsGeneratedKey()) {
                        readGeneratedKey(statement, context);
                    }
                    break;

                case BATCH:
                    BatchExecutionPlan batchPlan = (BatchExecutionPlan) plan;
                    for (Object[] parameters : batchPlan.getBatchParameters()) {
                        ParameterProcessor.bind(statement, parameters, plan.getParameterBinders());
                        statement.addBatch();
                    }
                    context.setBatchUpdateCounts(batchPlan.getBatchParameters().isEmpty()
                        ? new int[0]
                        : statement.executeBatch());
                    break;
                    
            }
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to execute SQL: " + plan.getSql(), e);
        }
    }

    private void readGeneratedKey(PreparedStatement statement, ExecutionContext context) throws SQLException {
        ResultSet generatedKeys = statement.getGeneratedKeys();
        context.setResultSet(generatedKeys);
        if (!generatedKeys.next()) {
            throw new SQLException("JDBC returned no generated key");
        }
        context.setGeneratedKey(generatedKeys.getObject(1));
        if (generatedKeys.next()) {
            throw new SQLException("JDBC returned more than one generated key");
        }
    }
}
