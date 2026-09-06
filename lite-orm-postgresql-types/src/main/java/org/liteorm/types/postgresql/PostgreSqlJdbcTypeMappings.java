package org.liteorm.types.postgresql;

import org.liteorm.annotation.JdbcTypeMapping;
import org.liteorm.api.JdbcTypeMappings;

import java.sql.JDBCType;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Official PostgreSQL JDBC value mappings supplied by LiteORM.
 */
@JdbcTypeMapping(
    javaType = UUID.class,
    jdbcType = JDBCType.OTHER,
    adapter = PostgreSqlUuidJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = LocalTime.class,
    jdbcType = JDBCType.TIME,
    adapter = PostgreSqlLocalTimeJdbcValueAdapter.class
)
@JdbcTypeMapping(
    javaType = OffsetDateTime.class,
    jdbcType = JDBCType.TIMESTAMP_WITH_TIMEZONE,
    adapter = PostgreSqlOffsetDateTimeJdbcValueAdapter.class
)
public final class PostgreSqlJdbcTypeMappings implements JdbcTypeMappings {
}
