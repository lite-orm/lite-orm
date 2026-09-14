package io.github.kervix.spring.boot;

import io.github.kervix.testsupport.database.DatabaseEngine;

class MySqlPackageDataSourceExecutionTest extends AbstractPackageDataSourceExecutionTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.MYSQL;
    }
}
