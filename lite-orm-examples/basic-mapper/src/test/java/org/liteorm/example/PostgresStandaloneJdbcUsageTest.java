package org.liteorm.example;

import org.liteorm.testsupport.database.DatabaseEngine;

class PostgresStandaloneJdbcUsageTest extends StandaloneJdbcUsageTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.POSTGRESQL;
    }
}
