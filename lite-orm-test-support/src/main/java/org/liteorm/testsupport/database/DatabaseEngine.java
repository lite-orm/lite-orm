package org.liteorm.testsupport.database;

/**
 * Production database engines used by LiteORM functional tests.
 */
public enum DatabaseEngine {

    POSTGRESQL("postgres:16.4-alpine"),
    MYSQL("mysql:8.4.0");

    private final String imageName;

    DatabaseEngine(String imageName) {
        this.imageName = imageName;
    }

    public String imageName() {
        return imageName;
    }
}
