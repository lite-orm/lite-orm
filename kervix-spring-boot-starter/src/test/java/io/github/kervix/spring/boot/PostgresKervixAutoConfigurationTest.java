package io.github.kervix.spring.boot;

import io.github.kervix.testsupport.database.DatabaseEngine;

class PostgresKervixAutoConfigurationTest extends KervixAutoConfigurationTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.POSTGRESQL;
    }
}
