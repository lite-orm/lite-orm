package org.liteorm.handler;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

/**
 * @author qingbozhang
 * @since 2024/10/26 19:50
 */
public class StatementHandler extends AbstractBaseHandler {

    @Override
    public <T> List<T> selectList(ChainContext<T> context) throws Exception {
        Connection connection = context.getConnection();
        String sql = context.getSql();

        try (Statement statement = connection.createStatement()) {
            if (context.isBatchMode()) {
                // 批量执行模式
                for (Object[] params : context.getBatchParams()) {
                    // 根据 params 格式化 SQL
                    String processedSql = formatSql(sql, params);
                    statement.addBatch(processedSql);
                }
                int[] results = statement.executeBatch();
                context.setResult(results);
            } else {
                // 单次执行模式
                int result = statement.executeUpdate(sql);
                context.setResult(result);
            }

            return getNext().selectList(context);
        }
    }

    private String formatSql(String sql, Object[] params) {
        // 格式化 SQL 语句，将 params 参数值填充到 SQL 中
        for (Object param : params) {
            sql = sql.replaceFirst("\\?", param.toString());
        }
        return sql;
    }
}
