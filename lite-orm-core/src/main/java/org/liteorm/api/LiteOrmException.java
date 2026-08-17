package org.liteorm.api;

/**
 * Base class for public LiteORM failures.
 * 
 * @author lite-orm
 * @since 2024/11/15
 */
public class LiteOrmException extends RuntimeException {
    
    public LiteOrmException(String message) {
        super(message);
    }
    
    public LiteOrmException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public LiteOrmException(Throwable cause) {
        super(cause);
    }
}
