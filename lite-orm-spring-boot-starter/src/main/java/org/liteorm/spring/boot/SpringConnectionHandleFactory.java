package org.liteorm.spring.boot;

import org.liteorm.api.ConnectionHandle;
import org.liteorm.api.ConnectionHandleFactory;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Creates per-execution handles backed by Spring DataSource synchronization.
 */
public final class SpringConnectionHandleFactory implements ConnectionHandleFactory {

    private final DataSource dataSource;

    public SpringConnectionHandleFactory(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    @Override
    public ConnectionHandle openHandle() {
        return new SpringConnectionHandle(dataSource);
    }
}
