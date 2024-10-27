package org.liteorm.handler;

import java.sql.Connection;

/**
 * @author 张庆波
 * @since 创建于 2024/10/26 19:32
 */
public class ConnectionHandler extends AbstractBaseHandler {

    @Override
    public void handle(ChainContext<?> context) throws Exception {
        try (Connection connection = context.getDataSource().getConnection()) {
            context.setConnection(connection);
            getNext().handle(context);
        }
    }
}
