package org.liteorm.processor;

import org.liteorm.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * 参数处理器
 * 职责：创建PreparedStatement并绑定参数（防SQL注入）
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class ParameterProcessor implements SqlProcessor {
    
    @Override
    public void process(SqlTask task, ExecutionContext context) {
        Connection connection = context.getConnection();
        if (connection == null) {
            throw new IllegalStateException("Connection is null");
        }
        
        try {
            // 创建PreparedStatement
            PreparedStatement statement = connection.prepareStatement(task.getSql());
            
            // 绑定参数（防SQL注入）
            Object[] parameters = task.getParameters();
            if (parameters != null) {
                for (int i = 0; i < parameters.length; i++) {
                    statement.setObject(i + 1, parameters[i]);
                }
            }
            
            context.setPreparedStatement(statement);
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to prepare statement or bind parameters", e);
        }
    }
}
