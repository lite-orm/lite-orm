package io.github.kervix.test.multidatasource;

import io.github.kervix.testsupport.database.DatabaseEngine;

class PostgresMultiDataSourceExecutionTest extends MultiDataSourceExecutionTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.POSTGRESQL;
    }
}
