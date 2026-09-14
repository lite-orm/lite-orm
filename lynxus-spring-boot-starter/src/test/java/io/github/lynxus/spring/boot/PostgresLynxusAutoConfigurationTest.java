package io.github.lynxus.spring.boot;

import io.github.lynxus.testsupport.database.DatabaseEngine;

class PostgresLynxusAutoConfigurationTest extends LynxusAutoConfigurationTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.POSTGRESQL;
    }
}
