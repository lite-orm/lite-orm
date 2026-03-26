package org.liteorm.api;

/**
 * 映射异常
 * 
 * 结果集映射到对象时发生的异常
 * 
 * @author lite-orm
 * @since 2024/11/15
 */
public class MappingException extends LiteOrmException {
    
    private final Class<?> targetType;
    private final Object[] row;
    
    public MappingException(String message, Class<?> targetType, Object[] row) {
        super(buildMessage(message, targetType, row));
        this.targetType = targetType;
        this.row = row;
    }
    
    public MappingException(String message, Class<?> targetType, Object[] row, Throwable cause) {
        super(buildMessage(message, targetType, row), cause);
        this.targetType = targetType;
        this.row = row;
    }
    
    private static String buildMessage(String message, Class<?> targetType, Object[] row) {
        StringBuilder sb = new StringBuilder(message);
        sb.append("\nTarget Type: ").append(targetType != null ? targetType.getName() : "null");
        if (row != null && row.length > 0) {
            sb.append("\nRow Data: [");
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(row[i]);
            }
            sb.append("]");
        }
        return sb.toString();
    }
    
    public Class<?> getTargetType() {
        return targetType;
    }
    
    public Object[] getRow() {
        return row;
    }
}

