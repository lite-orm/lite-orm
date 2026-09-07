# LiteORM PostgreSQL JDBC Types

`lite-orm-postgresql-types` provides LiteORM's official compile-time JDBC value mappings for PostgreSQL. The artifact contains declarative mapping metadata and stateless JDBC 4.2 adapters; it does not contain the annotation processor, the PostgreSQL driver, MyBatis, Testcontainers, or a runtime mapping registry.

## Installation

Add Core, this artifact, and the PostgreSQL JDBC driver to the application. The application remains responsible for choosing and configuring the driver version.

```xml
<dependency>
    <groupId>org.liteorm</groupId>
    <artifactId>lite-orm-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.liteorm</groupId>
    <artifactId>lite-orm-postgresql-types</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.3</version>
    <scope>runtime</scope>
</dependency>
```

Select the official collection in every package that directly contains PostgreSQL Mappers:

```java
@UseJdbcTypeMappings(PostgreSqlJdbcTypeMappings.class)
package com.example.postgresql.mapper;

import org.liteorm.annotation.UseJdbcTypeMappings;
import org.liteorm.types.postgresql.PostgreSqlJdbcTypeMappings;
```

Selection is exact-package only. LiteORM does not discover this artifact from the classpath and does not use a runtime registry, reflection, or `ServiceLoader` to select adapters.

## Supported Mappings

The collection is complete: it declares the database-independent mappings for `BigInteger`, boxed `Byte[]`, legacy date values, `Year`, `Month`, `YearMonth`, and `JapaneseDate`, plus PostgreSQL mappings for `UUID`, `LocalTime`, `OffsetDateTime`, `OffsetTime`, explicit `NCHAR`/`NVARCHAR` strings, and `String + SQLXML`. pgjdbc does not implement `setNString` or `getNString`, so non-null national-character values use PostgreSQL's ordinary Unicode string methods. pgjdbc also rejects `NCHAR` and `NVARCHAR` in `setNull`, so the adapter binds null with `VARCHAR`. SQLXML results materialize as `String` and release the driver resource before JDBC cleanup. The [Core GA contract](../../reference/core-contract.md#26-official-postgresql-type-mappings) is the authoritative source for JDBC types, PostgreSQL representations, and guaranteed semantics.

PostgreSQL `ARRAY` results can be declared as `Object[]` with `@ResultJdbcType(JDBCType.ARRAY)`; LiteORM materializes the Java array before calling `Array.free()`. ARRAY parameters require an explicit `@UseParameterBinder` because an `Object[]` does not identify the PostgreSQL element type needed to create a JDBC array. PostgreSQL does not expose its database-specific large-object representations through the Core `BLOB`, `CLOB`, or `NCLOB` mappings.

Database-independent declarations reuse Core adapter implementations; the compiler does not append Core mappings implicitly. Applications can select one explicit override collection through `@UseJdbcTypeMappings.overrides`. Generated Mappers create one instance of each used adapter, bind null with the resolved JDBC type, and call adapters directly. Direct `InputStream` and `Reader` results are rejected; large values must be consumed through a callback-scoped cursor and `@UseRowMapper`.

## Verification

Run the module tests, reactor consumer, and dependency-boundary check from the repository root:

```bash
mvn -pl lite-orm-postgresql-types -am test
mvn -pl lite-orm-examples/basic-mapper -am \
  -Dtest=PostgreSqlTypesArtifactConsumptionTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
scripts/verify-postgresql-types-dependencies.sh
```
