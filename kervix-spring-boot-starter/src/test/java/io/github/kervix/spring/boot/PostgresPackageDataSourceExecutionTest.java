package io.github.kervix.spring.boot;

import io.github.kervix.testsupport.database.DatabaseEngine;

class PostgresPackageDataSourceExecutionTest extends AbstractPackageDataSourceExecutionTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.POSTGRESQL;
    }
}
