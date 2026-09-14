package io.github.kervix.api;

/**
 * Transaction failure classified by the phase that owns the failure.
 * 
 * @author kervix
 * @since 2024/09/29
 */
public class TransactionException extends KervixException {
    
    /**
     * Transaction failure categories.
     */
    public enum Type {
        BEGIN_FAILED,
        COMMIT_FAILED,
        ROLLBACK_FAILED,
        ROLLBACK_ONLY,
        CLEANUP_FAILED,
        DOMAIN_MISMATCH
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
     * Returns a user-facing description of the transaction failure.
     */
    public String getFriendlyMessage() {
        return switch (type) {
            case BEGIN_FAILED -> "Transaction start failed: " + getMessage();
            case COMMIT_FAILED -> "Transaction commit failed: " + getMessage();
            case ROLLBACK_FAILED -> "Transaction rollback failed: " + getMessage();
            case ROLLBACK_ONLY -> "Transaction is marked rollback-only: " + getMessage();
            case CLEANUP_FAILED -> "Transaction cleanup failed: " + getMessage();
            case DOMAIN_MISMATCH -> "Transaction DataSource domain mismatch: " + getMessage();
        };
    }
}
