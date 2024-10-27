package org.liteorm.handler;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author 张庆波
 * @since 创建于 2024/10/27 10:49
 */
@Slf4j
public class TransactionHandler extends AbstractBaseHandler {

    @Override
    public void handle(ChainContext context) throws Exception {
        Connection connection = context.getConnection();
        try {
            beginTransaction(context);
            getNext().handle(context);
            commit(context);
        } catch (Exception e) {
            rollback(context);
        } finally {
            reset(context);
        }
    }

    private void beginTransaction(ChainContext context) throws SQLException {
        if (!context.isTransactionActive()) {
            context.getConnection().setAutoCommit(false);
            context.setTransactionActive(true);
        }
    }

    private void commit(ChainContext context) throws SQLException {
        if (context.isTransactionActive()) {
            context.getConnection().commit();
            context.getConnection().setAutoCommit(true);
            context.setTransactionActive(false);
        }
    }

    private void reset(ChainContext context) throws SQLException {
        if (context.isTransactionActive()) {
            context.getConnection().setAutoCommit(true);
            context.setTransactionActive(false);
        }
    }

    private void rollback(ChainContext context) throws SQLException {
        if (context.isTransactionActive()) {
            context.getConnection().rollback();
            context.getConnection().setAutoCommit(true);
            context.setTransactionActive(false);
        }
    }
}
