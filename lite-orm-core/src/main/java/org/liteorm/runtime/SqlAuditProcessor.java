package org.liteorm.runtime;

import org.liteorm.ExecutionContext;
import org.liteorm.api.ExecutionPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SQL审计处理器
 * 
 * 功能：
 * 1. 记录操作类型、时间戳、SQL
 * 2. 提供AuditListener接口扩展
 * 3. 支持异步审计（不影响主流程）
 * 
 * 使用场景：
 * - 合规审计要求
 * - 操作追踪
 * - 安全审查
 * - 数据变更记录
 * 
 * @author lite-orm
 * @since 2024/11/15
 */
public class SqlAuditProcessor implements SqlProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(SqlAuditProcessor.class);
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private final List<AuditListener> listeners = new ArrayList<>();
    private final ExecutorService executorService;
    private final boolean async;
    
    /**
     * 默认构造器 - 异步审计
     */
    public SqlAuditProcessor() {
        this(true);
    }
    
    /**
     * 自定义构造器
     * 
     * @param async 是否异步审计
     */
    public SqlAuditProcessor(boolean async) {
        this.async = async;
        this.executorService = async ? Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "LiteORM-Audit");
            thread.setDaemon(true);
            return thread;
        }) : null;
    }
    
    /**
     * 添加审计监听器
     */
    public void addListener(AuditListener listener) {
        listeners.add(listener);
    }
    
    @Override
    public void process(ExecutionPlan plan, ExecutionContext context) {
        // 在SQL执行完成后记录审计日志
        if (context.getQueryResults() != null || context.getUpdateCount() >= 0) {
            audit(plan, context);
        }
    }
    
    /**
     * 审计记录
     */
    private void audit(ExecutionPlan plan, ExecutionContext context) {
        AuditRecord record = createAuditRecord(plan, context);
        
        if (async && executorService != null) {
            // 异步审计
            executorService.submit(() -> processAuditRecord(record));
        } else {
            // 同步审计
            processAuditRecord(record);
        }
    }
    
    /**
     * 创建审计记录
     */
    private AuditRecord createAuditRecord(ExecutionPlan plan, ExecutionContext context) {
        Long startTime = (Long) context.getAttribute("sql.startTime");
        long duration = startTime != null ? System.currentTimeMillis() - startTime : 0;
        
        return new AuditRecord(
            LocalDateTime.now(),
            plan.getStatementType().name(),
            plan.getSql(),
            plan.getParameters(),
            duration,
            plan.getStatementType() == ExecutionPlan.StatementType.SELECT ?
                (context.getQueryResults() != null ? context.getQueryResults().size() : 0) :
                context.getUpdateCount()
        );
    }
    
    /**
     * 处理审计记录
     */
    private void processAuditRecord(AuditRecord record) {
        // 记录到日志
        if (logger.isInfoEnabled()) {
            logger.info("SQL Audit - {} | {} | {} | Duration: {}ms | Affected: {}",
                record.timestamp().format(formatter),
                record.operationType(),
                record.sql(),
                record.durationMillis(),
                record.affectedRows());
        }
        
        // 通知所有监听器
        for (AuditListener listener : listeners) {
            try {
                listener.onAudit(record);
            } catch (Exception e) {
                logger.error("Error in audit listener: " + listener.getClass().getName(), e);
            }
        }
    }
    
    /**
     * 关闭审计处理器
     */
    public void shutdown() {
        if (executorService != null) {
            executorService.shutdown();
        }
    }
    
    /**
     * 审计记录
     */
    public record AuditRecord(
        LocalDateTime timestamp,
        String operationType,
        String sql,
        Object[] parameters,
        long durationMillis,
        int affectedRows
    ) {}
    
    /**
     * 审计监听器接口
     * 用户可实现此接口来自定义审计处理逻辑
     */
    public interface AuditListener {
        /**
         * 审计事件回调
         * 
         * @param record 审计记录
         */
        void onAudit(AuditRecord record);
    }
}
