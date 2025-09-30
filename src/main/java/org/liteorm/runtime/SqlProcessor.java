package org.liteorm.runtime;

import org.liteorm.ExecutionContext;
import org.liteorm.api.SqlTask;

/**
 * SQL处理器接口
 * 责任链中的处理器抽象
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public interface SqlProcessor {
    
    /**
     * 处理SQL任务的某个环节
     * 
     * @param task SQL任务
     * @param context 执行上下文
     */
    void process(SqlTask task, ExecutionContext context);
}
