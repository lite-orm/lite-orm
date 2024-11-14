package org.liteorm.handler;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * @author qingbozhang
 * @since 2024/10/27 10:49
 */
@Slf4j
public class TransactionHandler extends AbstractBaseHandler {

    @Override
    public <T> List<T> selectList(ChainContext<T> context) throws Exception {
        Connection connection = context.getConnection();
        try {
            beginTransaction(context, connection);
            List<T> result = getNext().selectList(context);
            commit(context, connection);
            return result;
        } catch (Exception e) {
            rollback(context, connection);
            throw e;
        } finally {
            reset(context, connection);
        }
    }

    private void beginTransaction(ChainContext<?> context, Connection connection) throws SQLException {
        if (!context.isTransactionActive()) {
            connection.setAutoCommit(false);
            context.setTransactionActive(true);
            log.debug("begin transaction");
        }
    }

    private void commit(ChainContext<?> context, Connection connection) throws SQLException {
        if (context.isTransactionActive()) {
            connection.commit();
            connection.setAutoCommit(true);
            context.setTransactionActive(false);
            log.debug("commit transaction");
        }
    }

    private void reset(ChainContext<?> context, Connection connection) throws SQLException {
        if (context.isTransactionActive()) {
            connection.setAutoCommit(true);
            context.setTransactionActive(false);
            log.debug("reset transaction");
        }
    }

    private void rollback(ChainContext<?> context, Connection connection) throws SQLException {
        if (context.isTransactionActive()) {
            connection.rollback();
            connection.setAutoCommit(true);
            context.setTransactionActive(false);
            log.debug("rollback transaction");
        }
    }
}
