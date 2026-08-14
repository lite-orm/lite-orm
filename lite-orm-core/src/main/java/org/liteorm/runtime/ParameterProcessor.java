package org.liteorm.runtime;

import org.liteorm.ExecutionContext;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.ParameterBinder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

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
            PreparedStatement statement = plan.returnsGeneratedKey()
                ? connection.prepareStatement(plan.getSql(), Statement.RETURN_GENERATED_KEYS)
                : connection.prepareStatement(plan.getSql());
            
            // 绑定参数（防SQL注入）
            if (plan.getStatementType() != ExecutionPlan.StatementType.BATCH) {
                bind(statement, plan.getParameters(), plan.getParameterBinders());
            }
            
            context.setPreparedStatement(statement);
            
        } catch (SQLException e) {
            throw new RuntimeException("Failed to prepare statement or bind parameters", e);
        }
    }

    static void bind(PreparedStatement statement, Object[] parameters, ParameterBinder<?>[] binders)
            throws SQLException {
        if (parameters == null) {
            return;
        }
        for (int index = 0; index < parameters.length; index++) {
            ParameterBinder<Object> binder = binderAt(binders, index);
            if (binder != null) {
                binder.bind(statement, index + 1, parameters[index]);
            } else {
                statement.setObject(index + 1, parameters[index]);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static ParameterBinder<Object> binderAt(ParameterBinder<?>[] binders, int index) {
        if (binders == null || index >= binders.length) {
            return null;
        }
        return (ParameterBinder<Object>) binders[index];
    }
}
