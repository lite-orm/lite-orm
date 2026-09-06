package org.liteorm.example.mysqltypes;

import org.junit.jupiter.api.Test;
import org.liteorm.api.JdbcTypeMappingsMetadata;
import org.liteorm.api.SqlExecutor;
import org.liteorm.types.mysql.MySqlJdbcTypeMappings;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MySqlTypesArtifactConsumptionTest {

    @Test
    void reactorConsumerSelectsThePublishedMySqlMappings() {
        SqlExecutor sqlExecutor = plan -> {
            throw new AssertionError("The artifact-consumption test does not execute SQL");
        };

        MySqlTypesArtifactMapper mapper = new MySqlTypesArtifactMapperImpl(sqlExecutor);

        assertEquals(MySqlJdbcTypeMappings.class,
            ((JdbcTypeMappingsMetadata) mapper).jdbcTypeMappings());
    }
}
