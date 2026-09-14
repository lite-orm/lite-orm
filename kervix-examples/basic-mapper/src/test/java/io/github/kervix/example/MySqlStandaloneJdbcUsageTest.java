package io.github.kervix.example;

import io.github.kervix.testsupport.database.DatabaseEngine;

class MySqlStandaloneJdbcUsageTest extends StandaloneJdbcUsageTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.MYSQL;
    }
}
