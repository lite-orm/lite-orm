package io.github.kervix.spring.boot;

import io.github.kervix.testsupport.database.DatabaseEngine;

class MySqlKervixAutoConfigurationTest extends KervixAutoConfigurationTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.MYSQL;
    }
}
