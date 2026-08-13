package org.liteorm.runtime;

import org.liteorm.ExecutionContext;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.ParameterBinder;

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
    public void process(ExecutionPlan plan, ExecutionContext context) {
        Connection connection = context.getConnection();
        if (connection == null) {
            throw new IllegalStateException("Connection is null");
        }
        
        try {
            // 创建PreparedStatement
            PreparedStatement statement = connection.prepareStatement(plan.getSql());
            
            // 绑定参数（防SQL注入）
            Object[] parameters = plan.getParameters();
            ParameterBinder<?>[] binders = plan.getParameterBinders();
            if (parameters != null) {
                for (int i = 0; i < parameters.length; i++) {
                    ParameterBinder<Object> binder = binderAt(binders, i);
                    if (parameters[i] != null && binder != null) {
                        binder.bind(statement, i + 1, parameters[i]);
                    } else {
                        statement.setObject(i + 1, parameters[i]);
                    }
                }
            }
            
            context.setPreparedStatement(statement);
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to prepare statement or bind parameters", e);
        }
    }

    @SuppressWarnings("unchecked")
    private ParameterBinder<Object> binderAt(ParameterBinder<?>[] binders, int index) {
        if (binders == null || index >= binders.length) {
            return null;
        }
        return (ParameterBinder<Object>) binders[index];
    }
}
