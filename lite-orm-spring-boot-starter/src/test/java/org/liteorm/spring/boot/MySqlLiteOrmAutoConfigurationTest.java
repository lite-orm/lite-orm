package org.liteorm.spring.boot;

import org.liteorm.testsupport.database.DatabaseEngine;

class MySqlLiteOrmAutoConfigurationTest extends LiteOrmAutoConfigurationTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.MYSQL;
    }
}
