package io.github.kervix.spring.boot;

import io.github.kervix.api.ConnectionHandle;
import io.github.kervix.api.ConnectionHandleFactory;

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
