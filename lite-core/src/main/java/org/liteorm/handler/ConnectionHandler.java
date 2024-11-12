package org.liteorm.handler;

import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.List;

/**
 * @author qingbozhang
 * @since 创建于 2024/10/26 19:32
 */
@Slf4j
public class ConnectionHandler extends AbstractBaseHandler {

    private DataSource dataSource;

    public ConnectionHandler(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public <T> List<T> selectList(ChainContext<T> context) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            log.debug("get connection");
            context.setConnection(connection);
            return getNext().selectList(context);
        } finally {
            log.debug("close connection");
        }
    }
}
