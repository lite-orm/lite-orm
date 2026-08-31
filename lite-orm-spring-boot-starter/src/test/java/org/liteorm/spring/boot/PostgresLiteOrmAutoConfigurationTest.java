package org.liteorm.spring.boot;

import org.liteorm.testsupport.database.DatabaseEngine;

class PostgresLiteOrmAutoConfigurationTest extends LiteOrmAutoConfigurationTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.POSTGRESQL;
    }
}
