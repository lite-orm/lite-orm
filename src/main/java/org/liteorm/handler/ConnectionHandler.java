package org.liteorm.handler;

import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;

/**
 * @author 张庆波
 * @since 创建于 2024/10/26 19:32
 */
@Slf4j
public class ConnectionHandler extends AbstractBaseHandler {

    @Override
    public void handle(ChainContext<?> context) throws Exception {
        try (Connection connection = context.getDataSource().getConnection()) {
            log.debug("get connection");
            context.setConnection(connection);
            getNext().handle(context);
        } finally {
            log.debug("close connection");
        }
    }
}
