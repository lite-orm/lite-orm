# Select JDBC type mappings per Mapper package

LiteORM selects one explicit `JdbcTypeMappings` collection from each Mapper package's `package-info.java`, then generates direct calls to the collection's declared `JdbcValueAdapter` implementations. PostgreSQL and MySQL mappings ship as separate artifacts, while users and third parties may publish additional mapping artifacts. This keeps database-family value semantics available at compilation without adding command-line profiles, classpath auto-detection, a runtime type-handler registry, or SQL-dialect behavior to the mapping interface.
