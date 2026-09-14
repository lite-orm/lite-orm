package io.github.kervix.test.database;

import io.github.kervix.testsupport.database.DatabaseEngine;

class PostgresCoreConcurrencySoakTest extends CoreConcurrencySoakTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.POSTGRESQL;
    }
}
