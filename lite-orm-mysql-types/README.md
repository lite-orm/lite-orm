# LiteORM MySQL JDBC Types

`lite-orm-mysql-types` provides LiteORM's official compile-time JDBC value mappings for MySQL. The artifact contains declarative mapping metadata and stateless JDBC 4.2 adapters; it does not contain the annotation processor, the MySQL driver, MyBatis, Testcontainers, or a runtime mapping registry.

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

Selection is exact-package only. LiteORM does not discover this artifact from the classpath and does not use a runtime registry, reflection, or `ServiceLoader` to select adapters.

## Supported Mappings

The current artifact supports `UUID`, `LocalTime`, and `OffsetDateTime`. The [Core GA contract](../docs/core-ga-contract.md#27-official-mysql-type-mappings) is the authoritative source for their JDBC types, MySQL representations, and guaranteed semantics.

The generated Mapper creates one instance of each adapter it uses, binds null with the declared JDBC type, and calls the adapter directly for non-null parameters and supported results. Remaining deterministic scalar mappings and lifecycle-bound JDBC values are outside this artifact's current scope.

## Verification

Run the module tests, reactor consumer, and dependency-boundary check from the repository root:

```bash
mvn -pl lite-orm-mysql-types -am test
mvn -pl lite-orm-examples/basic-mapper -am \
  -Dtest=MySqlTypesArtifactConsumptionTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
scripts/verify-mysql-types-dependencies.sh
```
