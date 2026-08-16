package org.liteorm.api;

/**
 * 事务异常 - 基于物理必需性的异常分类
 * 
 * 物理分类原理：
 * - 每种异常对应事务的不同物理阶段
 * - 提供详细的错误分类便于诊断
 * - 支持异常链和原因跟踪
 * 
 * @author lite-orm
 * @since 2024/09/29
 */
public class TransactionException extends LiteOrmException {
    
    /**
     * 事务异常类型 - 基于物理阶段分类
     */
    public enum Type {
        BEGIN_FAILED,    // 开始事务失败（连接、权限等问题）
        COMMIT_FAILED,   // 提交失败（约束、锁等问题）
        ROLLBACK_FAILED, // 回滚失败（连接断开等问题）
        ROLLBACK_ONLY,   // 嵌套工作失败后根事务禁止提交
        CLEANUP_FAILED,  // 事务完成后的连接状态恢复或释放失败
        DOMAIN_MISMATCH  // 事务期间选择了另一个 DataSource 域
    }
    
    private final Type type;
    
    public TransactionException(Type type, String message) {
        super(buildMessage(type, message));
        this.type = type;
    }
    
    public TransactionException(Type type, String message, Throwable cause) {
        super(buildMessage(type, message), cause);
        this.type = type;
    }
    
    public Type getType() {
        return type;
    }

    public ExecutionPhase getPhase() {
        return ExecutionPhase.TRANSACTION;
    }

    private static String buildMessage(Type type, String message) {
        return message + " [phase=" + ExecutionPhase.TRANSACTION + ", type=" + type + "]";
    }
    
    /**
     * 获取用户友好的错误描述
     */
    public String getFriendlyMessage() {
        return switch (type) {
            case BEGIN_FAILED -> "事务启动失败: " + getMessage();
            case COMMIT_FAILED -> "事务提交失败: " + getMessage();
            case ROLLBACK_FAILED -> "事务回滚失败: " + getMessage();
            case ROLLBACK_ONLY -> "事务已标记为仅回滚: " + getMessage();
            case CLEANUP_FAILED -> "事务清理失败: " + getMessage();
            case DOMAIN_MISMATCH -> "事务数据源域不匹配: " + getMessage();
        };
    }
}
