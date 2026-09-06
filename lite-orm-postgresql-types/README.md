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

The current artifact supports `UUID`, `LocalTime`, and `OffsetDateTime`. The [Core GA contract](../docs/core-ga-contract.md#26-official-postgresql-type-mappings) is the authoritative source for their JDBC types, PostgreSQL representations, and guaranteed semantics.

The generated Mapper creates one instance of each adapter it uses, binds null with the declared JDBC type, and calls the adapter directly for non-null parameters and supported results. Remaining deterministic scalar mappings and lifecycle-bound JDBC values are outside this artifact's current scope.

## Verification

Run the module tests, reactor consumer, and dependency-boundary check from the repository root:

```bash
mvn -pl lite-orm-postgresql-types -am test
mvn -pl lite-orm-examples/basic-mapper -am \
  -Dtest=PostgreSqlTypesArtifactConsumptionTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
scripts/verify-postgresql-types-dependencies.sh
```
