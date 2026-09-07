# LiteORM MySQL JDBC Types

`lite-orm-mysql-types` provides LiteORM's official JDBC value mappings for MySQL. The artifact contains declarative mapping metadata and stateless JDBC 4.2 type handlers; it does not contain the annotation processor, the MySQL driver, MyBatis, Testcontainers, or a mutable runtime registry.

## Installation

Add Core, this artifact, and the MySQL JDBC driver to the application. The application remains responsible for choosing and configuring the driver version.

```xml
<dependency>
    <groupId>org.liteorm</groupId>
    <artifactId>lite-orm-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>org.liteorm</groupId>
    <artifactId>lite-orm-mysql-types</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.2.0</version>
    <scope>runtime</scope>
</dependency>
```

Select the official collection in every package that directly contains MySQL Mappers:

```java
@UseJdbcTypeMappings(MySqlJdbcTypeMappings.class)
package com.example.mysql.mapper;

import org.liteorm.annotation.UseJdbcTypeMappings;
import org.liteorm.types.mysql.MySqlJdbcTypeMappings;
```

Selection is exact-package only. LiteORM does not discover this artifact from the classpath and does not use a global registry, reflection, or `ServiceLoader` to select handlers.

## Supported Mappings

The collection is complete: it declares the database-independent mappings for `BigInteger`, boxed `Byte[]`, legacy date values, `Year`, `Month`, `YearMonth`, and `JapaneseDate`, plus MySQL mappings for `UUID`, `LocalTime`, `OffsetDateTime`, and explicit `NCHAR`/`NVARCHAR` strings. National-character values use the JDBC `setNString` and `getNString` methods. The [Core GA contract](../../reference/core-contract.md#27-official-mysql-type-mappings) is the authoritative source for JDBC types, MySQL representations, and guaranteed semantics.

Database-independent declarations reuse Core handler implementations; the compiler does not append Core mappings implicitly. Applications can select one explicit override collection through `@UseJdbcTypeMappings.overrides`. Generated Mappers create one instance of each handler and an immutable type router. Parameters use generated Java types and optional `jdbcType`; results use JDBC metadata resolved once per result set. Lifecycle-bound JDBC values remain outside this artifact's current scope.

## Verification

Run the module tests, reactor consumer, and dependency-boundary check from the repository root:

```bash
mvn -pl lite-orm-mysql-types -am test
mvn -pl lite-orm-examples/basic-mapper -am \
  -Dtest=MySqlTypesArtifactConsumptionTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
scripts/verify-mysql-types-dependencies.sh
```
