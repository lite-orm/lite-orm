package io.github.lynxus.it;

import io.github.lynxus.api.SqlExecutor;

public final class GeneratedMapperConsumer {

    private GeneratedMapperConsumer() {
    }

    public static ExternalUserMapper create(SqlExecutor sqlExecutor) {
        return new ExternalUserMapperImpl(sqlExecutor);
    }
}
