package org.liteorm;

import org.liteorm.api.ConnectionProvider;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.api.ExecutionInterceptor;
import org.liteorm.api.ExecutionInvocation;
import org.liteorm.api.SqlEngine;
import org.liteorm.api.SqlResult;
import org.liteorm.api.TransactionCoordinator;
import org.liteorm.runtime.ConnectionProcessor;
import org.liteorm.runtime.ExecutionProcessor;
import org.liteorm.runtime.ParameterProcessor;
import org.liteorm.runtime.ResultProcessor;
import org.liteorm.runtime.SqlProcessor;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

/**
 * 默认SQL执行引擎实现 - 基于物理必需性的可配置责任链
 * 
 * 核心改进（基于第一性原理）：
 * 1. 处理器列表可外部传入 - 支持扩展
 * 2. 事务管理支持自管理和外部管理
 * 3. 每个处理器都有明确的物理职责
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class DefaultSqlEngine implements SqlEngine {
    
    private final List<SqlProcessor> processors;
    private final ConnectionProvider connectionProvider;
    private final List<ExecutionInterceptor> interceptors;
    
    /**
     * 默认构造器 - 使用标准的5个物理必需处理器
     */
    public DefaultSqlEngine(ConnectionProvider connectionProvider) {
        this(connectionProvider, () -> null, defaultProcessors(), List.of());
    }
    
    /**
     * 可配置构造器 - 支持外部传入处理器列表
     * 这满足了"不写死"的需求，支持扩展
     */
    public DefaultSqlEngine(ConnectionProvider connectionProvider, List<SqlProcessor> processors) {
        this(connectionProvider, processors, List.of());
    }

    public DefaultSqlEngine(ConnectionProvider connectionProvider, List<SqlProcessor> processors,
                            List<ExecutionInterceptor> interceptors) {
        this(connectionProvider, () -> null, processors, interceptors);
    }

    public DefaultSqlEngine(ConnectionProvider connectionProvider, TransactionCoordinator transactionCoordinator,
                            List<SqlProcessor> processors, List<ExecutionInterceptor> interceptors) {
        this.connectionProvider = connectionProvider;
        List<SqlProcessor> configuredProcessors = new ArrayList<>(processors.size() + 1);
        configuredProcessors.add(new ConnectionProcessor(connectionProvider, transactionCoordinator));
        configuredProcessors.addAll(processors);
        this.processors = List.copyOf(configuredProcessors);
        this.interceptors = List.copyOf(interceptors);
    }
    
    /**
     * 创建默认的5个物理必需处理器
     * 基于SQL执行的物理步骤：连接→事务→参数→执行→结果
     */
    private static List<SqlProcessor> defaultProcessors() {
        return Arrays.asList(
            new ParameterProcessor(),
            new ExecutionProcessor(),
            new ResultProcessor()
        );
    }
    
    @Override
    public SqlResult execute(ExecutionPlan plan) {
        // 创建执行上下文
        ExecutionContext context = new ExecutionContext();
        ExecutionInvocation invocation = new ExecutionInvocation(plan);
        List<ExecutionInterceptor> enteredInterceptors = new ArrayList<>(interceptors.size());
        
        try {
            for (ExecutionInterceptor interceptor : interceptors) {
                enteredInterceptors.add(interceptor);
                interceptor.beforeExecution(invocation);
            }

            // 按顺序执行所有处理器 - 这是物理执行链
            for (SqlProcessor processor : processors) {
                processor.process(plan, context);
            }
            
            // 构造成功结果
            SqlResult result = createSuccessResult(context, plan);
            invocation.complete(
                plan.getStatementType() == ExecutionPlan.StatementType.SELECT ? 0 : context.getUpdateCount(),
                context.getQueryResults() == null ? 0 : context.getQueryResults().size(),
                null
            );
            invokeSuccessCallbacks(enteredInterceptors, invocation);
            return result;
            
        } catch (Exception e) {
            invocation.complete(
                plan.getStatementType() == ExecutionPlan.StatementType.SELECT ? 0 : context.getUpdateCount(),
                context.getQueryResults() == null ? 0 : context.getQueryResults().size(),
                e
            );
            invokeFailureCallbacks(enteredInterceptors, invocation, e);
            return SqlResult.error(e);
        } finally {
            closeExecutionResources(context);
        }
    }

    private void invokeSuccessCallbacks(
            List<ExecutionInterceptor> enteredInterceptors, ExecutionInvocation invocation) {
        for (int index = enteredInterceptors.size() - 1; index >= 0; index--) {
            enteredInterceptors.get(index).afterSuccess(invocation);
        }
    }

    private void invokeFailureCallbacks(
            List<ExecutionInterceptor> enteredInterceptors, ExecutionInvocation invocation, Exception failure) {
        for (int index = enteredInterceptors.size() - 1; index >= 0; index--) {
            try {
                enteredInterceptors.get(index).afterFailure(invocation);
            } catch (Exception callbackFailure) {
                failure.addSuppressed(callbackFailure);
            }
        }
    }

    private void closeExecutionResources(ExecutionContext context) {
        try {
            if (context.getResultSet() != null) {
                context.getResultSet().close();
            }
        } catch (Exception ignored) {
        }
        try {
            if (context.getPreparedStatement() != null) {
                context.getPreparedStatement().close();
            }
        } catch (Exception ignored) {
        }
        if (context.getConnection() != null && !context.isInTransaction()) {
            connectionProvider.release(context.getConnection());
        }
    }
    
    /**
     * 构造成功结果
     */
    private SqlResult createSuccessResult(ExecutionContext context, ExecutionPlan plan) {
        switch (plan.getStatementType()) {
            case SELECT:
                return SqlResult.success(context.getQueryResults());
            case INSERT:
            case UPDATE:
            case DELETE:
                return SqlResult.success(context.getUpdateCount());
            default:
                throw new UnsupportedOperationException("Unsupported SQL type: " + plan.getStatementType());
        }
    }
    
}
