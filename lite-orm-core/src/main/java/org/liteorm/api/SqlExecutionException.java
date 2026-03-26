package org.liteorm.api;

/**
 * SQL执行异常
 * 
 * SQL执行过程中发生的异常
 * 
 * @author lite-orm
 * @since 2024/11/15
 */
public class SqlExecutionException extends LiteOrmException {
    
    private final String sql;
    private final Object[] parameters;
    
    public SqlExecutionException(String message, String sql, Object[] parameters) {
        super(buildMessage(message, sql, parameters));
        this.sql = sql;
        this.parameters = parameters;
    }
    
    public SqlExecutionException(String message, String sql, Object[] parameters, Throwable cause) {
        super(buildMessage(message, sql, parameters), cause);
        this.sql = sql;
        this.parameters = parameters;
    }
    
    private static String buildMessage(String message, String sql, Object[] parameters) {
        StringBuilder sb = new StringBuilder(message);
        sb.append("\nSQL: ").append(sql);
        if (parameters != null && parameters.length > 0) {
            sb.append("\nParameters: [");
            for (int i = 0; i < parameters.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(parameters[i]);
            }
            sb.append("]");
        }
        return sb.toString();
    }
    
    public String getSql() {
        return sql;
    }
    
    public Object[] getParameters() {
        return parameters;
    }
}

