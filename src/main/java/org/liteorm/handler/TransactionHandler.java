package org.liteorm.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author 张庆波
 * @since 创建于 2024/10/27 10:49
 */
public class TransactionHandler extends AbstractBaseHandler {

    private static final Logger log = LoggerFactory.getLogger(TransactionHandler.class);

    @Override
    public void handle(ChainContext context) {
        Connection connection = context.getConnection();
        try {
            if (context.isTransactionActive()) {
                // 关闭自动提交，手动管理事务
                connection.setAutoCommit(false);
            }
            if (getNext() != null) {
                getNext().handle(context);
            }
            if (context.isTransactionActive()) {
                connection.commit();  // 提交事务
            }
        } catch (Exception e) {
            if (context.isTransactionActive()) {
                // 出现异常时回滚事务
                try {
                    connection.rollback();
                } catch (SQLException ex) {
                    throw new RuntimeException(ex);
                }
                log.error("Transaction rolled back due to: ", e);
            }
        } finally {
            if (context.isTransactionActive()) {
                // 恢复自动提交
                try {
                    connection.setAutoCommit(true);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
