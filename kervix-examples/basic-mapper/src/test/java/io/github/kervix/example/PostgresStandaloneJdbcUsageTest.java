package io.github.kervix.example;

import io.github.kervix.testsupport.database.DatabaseEngine;

class PostgresStandaloneJdbcUsageTest extends StandaloneJdbcUsageTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.POSTGRESQL;
    }
}
