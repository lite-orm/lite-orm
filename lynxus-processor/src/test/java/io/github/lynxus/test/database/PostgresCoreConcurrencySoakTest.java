package io.github.lynxus.test.database;

import io.github.lynxus.testsupport.database.DatabaseEngine;

class PostgresCoreConcurrencySoakTest extends CoreConcurrencySoakTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.POSTGRESQL;
    }
}
