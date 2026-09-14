package io.github.lynxus.spring.boot;

import io.github.lynxus.testsupport.database.DatabaseEngine;

class MySqlLynxusAutoConfigurationTest extends LynxusAutoConfigurationTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.MYSQL;
    }
}
