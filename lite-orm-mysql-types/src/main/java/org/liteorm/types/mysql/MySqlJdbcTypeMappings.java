package org.liteorm.types.mysql;

import org.liteorm.annotation.JdbcTypeMapping;
import org.liteorm.api.JdbcTypeMappings;

import java.sql.JDBCType;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Official MySQL JDBC value mappings supplied by LiteORM.
 */
@JdbcTypeMapping(
    javaType = UUID.class,
    jdbcType = JDBCType.CHAR,
    adapter = MySqlUuidJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = LocalTime.class,
    jdbcType = JDBCType.TIME,
    adapter = MySqlLocalTimeJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = OffsetDateTime.class,
    jdbcType = JDBCType.TIMESTAMP,
    adapter = MySqlOffsetDateTimeJdbcValueAdapter.class
)
public final class MySqlJdbcTypeMappings implements JdbcTypeMappings {
}
