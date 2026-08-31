package org.liteorm.spring.boot;

import org.liteorm.testsupport.database.DatabaseEngine;

class PostgresPackageDataSourceExecutionTest extends AbstractPackageDataSourceExecutionTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.POSTGRESQL;
    }
}
