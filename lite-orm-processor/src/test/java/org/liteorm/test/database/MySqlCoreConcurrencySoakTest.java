package org.liteorm.test.database;

import org.liteorm.testsupport.database.DatabaseEngine;

class MySqlCoreConcurrencySoakTest extends CoreConcurrencySoakTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.MYSQL;
    }
}
