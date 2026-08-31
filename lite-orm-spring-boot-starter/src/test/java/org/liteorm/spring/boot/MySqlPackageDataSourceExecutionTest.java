package org.liteorm.spring.boot;

import org.liteorm.testsupport.database.DatabaseEngine;

class MySqlPackageDataSourceExecutionTest extends AbstractPackageDataSourceExecutionTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.MYSQL;
    }
}
