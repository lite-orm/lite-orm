package io.github.lynxus.example;

import io.github.lynxus.testsupport.database.DatabaseEngine;

class PostgresStandaloneJdbcUsageTest extends StandaloneJdbcUsageTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.POSTGRESQL;
    }
}
