package org.liteorm.example;

import org.liteorm.testsupport.database.DatabaseEngine;

class MySqlStandaloneJdbcUsageTest extends StandaloneJdbcUsageTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.MYSQL;
    }
}
