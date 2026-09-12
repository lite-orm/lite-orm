package org.liteorm.test.multidatasource;

import org.liteorm.testsupport.database.DatabaseEngine;

class PostgresMultiDataSourceExecutionTest extends MultiDataSourceExecutionTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.POSTGRESQL;
    }
}
