package org.liteorm.spring.boot;

import org.liteorm.ExecutionContext;
import org.liteorm.api.ExecutionPlan;
import org.liteorm.runtime.SqlProcessor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Spring事务集成处理器
 * 
 * 功能：
 * 1. 检测Spring事务上下文
 * 2. 复用Spring管理的事务连接
 * 3. 支持@Transactional声明式事务
 * 
 * @author lite-orm
 * @since 2024/11/15
 */
public class SpringTransactionProcessor implements SqlProcessor {
    
    @Override
    public void process(ExecutionPlan plan, ExecutionContext context) {
        // 检查是否在Spring事务中
        boolean inTransaction = TransactionSynchronizationManager.isActualTransactionActive();
        
        if (inTransaction) {
            // 标记为Spring管理的事务
            context.setAttribute("spring.transaction.active", true);
            
            // Spring会自动管理连接和事务，LiteORM只需要使用即可
            // ConnectionProcessor会从DataSource获取连接，Spring会返回事务连接
        }
    }
}
