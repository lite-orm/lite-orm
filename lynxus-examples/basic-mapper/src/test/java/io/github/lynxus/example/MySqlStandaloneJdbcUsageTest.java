package io.github.lynxus.example;

import io.github.lynxus.testsupport.database.DatabaseEngine;

class MySqlStandaloneJdbcUsageTest extends StandaloneJdbcUsageTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.MYSQL;
    }
}
