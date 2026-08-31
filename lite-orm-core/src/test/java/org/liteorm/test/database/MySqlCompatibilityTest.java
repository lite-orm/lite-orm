package org.liteorm.test.database;

import org.liteorm.testsupport.database.DatabaseEngine;

class MySqlCompatibilityTest extends AbstractDatabaseCompatibilityTest {

    @Override
    protected DatabaseEngine databaseEngine() {
        return DatabaseEngine.MYSQL;
    }

    @Override
    protected String identityDefinition() {
        return "BIGINT AUTO_INCREMENT PRIMARY KEY";
    }

    @Override
    protected String binaryDefinition() {
        return "VARBINARY(255)";
    }

    @Override
    protected String uuidDefinition() {
        return "CHAR(36)";
    }

    @Override
    protected String localTimeDefinition() {
        return "TIME(6)";
    }

    @Override
    protected String offsetDateTimeDefinition() {
        return "TIMESTAMP(6)";
    }

    @Override
    protected String sleepSql() {
        return "SELECT SLEEP(3)";
    }
}
