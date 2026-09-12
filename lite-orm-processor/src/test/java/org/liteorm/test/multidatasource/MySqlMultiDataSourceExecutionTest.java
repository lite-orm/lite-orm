package org.liteorm.test.multidatasource;

import org.liteorm.testsupport.database.DatabaseEngine;

class MySqlMultiDataSourceExecutionTest extends MultiDataSourceExecutionTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.MYSQL;
    }
}
