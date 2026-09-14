package org.mybatis.spring.sample.mapper;

import io.github.lynxus.api.SqlExecutor;

/**
 * Proves that the generated Mapper is consumable through the public runtime API.
 */
public final class GeneratedMapperConsumer {

  private GeneratedMapperConsumer() {
  }

  public static UserMapper create(SqlExecutor sqlExecutor) {
    return new UserMapperImpl(sqlExecutor);
  }
}
