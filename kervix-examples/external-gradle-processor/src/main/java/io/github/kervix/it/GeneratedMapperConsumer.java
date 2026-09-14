package io.github.kervix.it;

import io.github.kervix.api.SqlExecutor;

public final class GeneratedMapperConsumer {

    private GeneratedMapperConsumer() {
    }

    public static ExternalUserMapper create(SqlExecutor sqlExecutor) {
        return new ExternalUserMapperImpl(sqlExecutor);
    }
}
