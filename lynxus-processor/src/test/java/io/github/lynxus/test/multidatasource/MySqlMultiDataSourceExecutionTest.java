package io.github.lynxus.test.multidatasource;

import io.github.lynxus.testsupport.database.DatabaseEngine;

class MySqlMultiDataSourceExecutionTest extends MultiDataSourceExecutionTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.MYSQL;
    }
}
