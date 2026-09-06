package org.liteorm.example.postgresqltypes;

import org.junit.jupiter.api.Test;
import org.liteorm.api.JdbcTypeMappingsMetadata;
import org.liteorm.api.SqlExecutor;
import org.liteorm.types.postgresql.PostgreSqlJdbcTypeMappings;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PostgreSqlTypesArtifactConsumptionTest {

    @Test
    void reactorConsumerSelectsThePublishedPostgreSqlMappings() {
        SqlExecutor sqlExecutor = plan -> {
            throw new AssertionError("The artifact-consumption test does not execute SQL");
        };

        PostgreSqlTypesArtifactMapper mapper = new PostgreSqlTypesArtifactMapperImpl(sqlExecutor);

        assertEquals(PostgreSqlJdbcTypeMappings.class,
            ((JdbcTypeMappingsMetadata) mapper).jdbcTypeMappings());
    }
}
