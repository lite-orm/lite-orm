package org.liteorm.it;

import org.liteorm.api.SqlExecutor;

public final class GeneratedMapperConsumer {

    private GeneratedMapperConsumer() {
    }

    public static ExternalUserMapper create(SqlExecutor sqlExecutor) {
        return new ExternalUserMapperImpl(sqlExecutor);
    }
}
