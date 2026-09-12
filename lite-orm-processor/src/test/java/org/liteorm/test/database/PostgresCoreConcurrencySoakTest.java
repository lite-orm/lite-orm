package org.liteorm.test.database;

import org.liteorm.testsupport.database.DatabaseEngine;

class PostgresCoreConcurrencySoakTest extends CoreConcurrencySoakTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.POSTGRESQL;
    }
}
