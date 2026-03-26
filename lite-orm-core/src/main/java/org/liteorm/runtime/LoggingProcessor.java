package org.liteorm.runtime;

import org.liteorm.ExecutionContext;
import org.liteorm.api.ExecutionPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SQL日志处理器
 * 
 * 功能：
 * 1. 记录SQL模板和参数
 * 2. 记录执行耗时
 * 3. 支持DEBUG/INFO级别
 * 4. 可配置是否输出参数值
 * 
 * 使用场景：
 * - 开发环境调试
 * - 生产环境SQL跟踪
 * - 性能分析
 * 
 * @author lite-orm
 * @since 2024/11/15
 */
public class LoggingProcessor implements SqlProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingProcessor.class);
    private static final String START_TIME_KEY = "sql.startTime";
    
    private final boolean logParameters;
    private final boolean logResults;
    
    /**
     * 默认构造器 - 记录参数和结果
     */
    public LoggingProcessor() {
        this(true, false);
    }
    
    /**
     * 自定义构造器
     * 
     * @param logParameters 是否记录参数
     * @param logResults 是否记录结果数量
     */
    public LoggingProcessor(boolean logParameters, boolean logResults) {
        this.logParameters = logParameters;
        this.logResults = logResults;
    }
    
    @Override
    public void process(ExecutionPlan plan, ExecutionContext context) {
        // 前置处理：记录SQL和参数
        if (context.getPreparedStatement() == null) {
            logBefore(plan, context);
        }
        
        // 后置处理：记录执行时间和结果
        if (context.getQueryResults() != null || context.getUpdateCount() >= 0) {
            logAfter(plan, context);
        }
    }
    
    /**
     * 执行前记录
     */
    private void logBefore(ExecutionPlan plan, ExecutionContext context) {
        if (logger.isDebugEnabled()) {
            // 记录开始时间
            context.setAttribute(START_TIME_KEY, System.currentTimeMillis());
            
            StringBuilder sb = new StringBuilder();
            sb.append("\n┌─ SQL Execution Start ─────────────────────────");
            sb.append("\n│ Type: ").append(plan.getStatementType());
            sb.append("\n│ SQL: ").append(plan.getSql());
            
            if (logParameters && plan.getParameters() != null && plan.getParameters().length > 0) {
                sb.append("\n│ Parameters: [");
                Object[] params = plan.getParameters();
                for (int i = 0; i < params.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(formatParameter(params[i]));
                }
                sb.append("]");
            }
            
            sb.append("\n└───────────────────────────────────────────────");
            logger.debug(sb.toString());
        }
    }
    
    /**
     * 执行后记录
     */
    private void logAfter(ExecutionPlan plan, ExecutionContext context) {
        if (logger.isDebugEnabled()) {
            Long startTime = (Long) context.getAttribute(START_TIME_KEY);
            long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
            
            StringBuilder sb = new StringBuilder();
            sb.append("\n┌─ SQL Execution Complete ──────────────────────");
            sb.append("\n│ Duration: ").append(duration).append(" ms");
            
            if (logResults) {
                if (plan.getStatementType() == ExecutionPlan.StatementType.SELECT) {
                    int count = context.getQueryResults() != null ? context.getQueryResults().size() : 0;
                    sb.append("\n│ Rows: ").append(count);
                } else {
                    sb.append("\n│ Affected: ").append(context.getUpdateCount());
                }
            }
            
            sb.append("\n└───────────────────────────────────────────────");
            logger.debug(sb.toString());
        }
    }
    
    /**
     * 格式化参数值
     */
    private String formatParameter(Object param) {
        if (param == null) {
            return "null";
        }
        if (param instanceof String) {
            return "\"" + param + "\"";
        }
        return param.toString();
    }
}
