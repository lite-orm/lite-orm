package io.github.lynxus.spring.boot;

import io.github.lynxus.testsupport.database.DatabaseEngine;

class PostgresPackageDataSourceExecutionTest extends AbstractPackageDataSourceExecutionTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.POSTGRESQL;
    }
}
