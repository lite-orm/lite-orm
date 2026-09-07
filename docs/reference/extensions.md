# Extension Contracts

LiteORM keeps generated Mapper code as the default. Extensions are narrow, explicit escape hatches for cases that cannot remain fully generated.

For the top-level mental model and a decision diagram, start with [Choosing a Value or Row Mapping](../user/core/mapping.md). This document owns the precise validation and lifecycle contracts.

## Guarantee Levels

| Path | Compile-time guarantees | Runtime work |
| --- | --- | --- |
| Generated annotations/XML | Strongest: SQL source, supported expressions, parameter order, and built-in mapping are generated | JDBC execution only |
| Typed extension | Extension class, generic compatibility, visibility, constructor, and invocation are compile-time-bound | Explicit provider, binder, mapper, or interceptor code runs |
| Raw JDBC | Outside LiteORM generation guarantees | Application owns SQL, binding, mapping, resources, and diagnostics |

## Concurrency Contract

Generated Mapper implementations and `JdbcSqlExecutor` are designed for concurrent reuse. Each Mapper call builds its own immutable execution plan, while simple transaction state and Spring transaction-bound connections remain isolated by thread.

Generated code keeps one TypeHandler, Provider, Binder, and RowMapper instance per Mapper instance. `JdbcSqlExecutor` also reuses its configured Interceptor instances, and Spring normally supplies those interceptors as singleton beans. Therefore every TypeHandler, Provider, Binder, RowMapper, and Interceptor implementation must be stateless, thread-safe, or protect its mutable state with external synchronization. LiteORM does not clone extension instances per call and provides no stateful-handler factory contract.

## Spring Package-to-DataSource Binding

Spring registration uses explicit package bindings rather than type-only selection:

```yaml
lite-orm:
  mapper-bindings:
    - package-name: com.example.user.mapper
      data-source: usersDataSource
    - package-name: com.example.order.mapper
      data-source: ordersDataSource
```

- Every package rule names exactly one Spring `DataSource` bean.
- The named bean may be a physical pool or a routing DataSource proxy.
- Starter uses `SpringConnectionHandleFactory` and core `JdbcAssembly` to create the executor.
- Duplicate, parent, and child package rules cannot overlap.
- Every Mapper interface is registered once and belongs to one DataSource domain. Applications with multiple DataSources use disjoint Mapper package bindings.
- Bindings remain explicit when only one DataSource exists; the starter does not infer a default DataSource or Mapper scan package.
- Generated Mapper implementations remain plain Java classes without Spring component or injection annotations.
- Binding happens during application startup. Mapper invocation still calls its final executor field directly and performs no package or bean lookup.
- Each Spring transaction boundary must use the `PlatformTransactionManager` associated with the same DataSource as the selected executor.

## Host Lifecycle Ownership

A host integration may provide a `ConnectionHandleFactory` that participates in host-bound connections and may own transaction commit or rollback. These are the only lifecycle responsibilities replaced by the host.

`JdbcSqlExecutor` always owns statement preparation, statement options, parameter binding, SQL execution, generated-key handling, result reading, result mapping, cursor deactivation, executor-owned cleanup, final outcome formation, and terminal interceptor delivery. Spring Starter assembles and reuses that core executor; it must not copy, wrap into a second phase lifecycle, or reimplement those JDBC operations.

Closing a host-aware `ConnectionHandle` releases one executor participation. It does not claim that the physical connection was closed or that the host transaction committed or rolled back. Transaction completion remains outside `ExecutionOutcome`.

The boundary is verified by the core JDBC, cursor, and standalone transaction tests plus the Spring Starter integration suite:

```bash
mvn -pl lite-orm-core -Dtest=JdbcSqlExecutorTest,JdbcCursorExecutionTest,SimpleTransactionTest test
mvn -pl lite-orm-spring-boot-starter -am test
rg -n 'prepareStatement|executeQuery|executeUpdate|getGeneratedKeys' lite-orm-spring-boot-starter/src/main/java
```

## Standalone Assembly And Transactions

Create one immutable `JdbcAssembly` per DataSource domain:

```java
JdbcAssembly assembly = LiteOrm.jdbc(dataSource)
    .domain("users")
    .interceptors(interceptors)
    .build();
```

- `assembly.sqlExecutor()` is injected into generated Mapper implementations.
- `assembly.transactionalExecutor()` creates the explicit local callback boundary.
- Calls outside a callback use temporary auto-commit handles.
- Calls inside a callback join one thread-bound root `SimpleTransaction`.
- Nested callbacks join the root; only the outer callback completes it.
- One assembly never coordinates commit with another assembly.

## SQL Provider

Use `@UseSqlProvider` on a Mapper method with a concrete `SqlProvider<P>` implementation.

- Use it for exceptional runtime SQL structure, not ordinary optional predicates.
- Return non-blank immutable `BoundSql` with a non-null ordered parameter list.
- The provider cannot be combined with XML or SQL annotations on the same method.
- Generated code holds one provider instance and invokes it directly without reflection.
- Provider SQL has weaker compile-time SQL validation because its final text is created at runtime.

## Parameter Binder

Use `@UseParameterBinder` on a Mapper parameter with a concrete `ParameterBinder<T>`.

- Use it when the package type-handler policy is not appropriate for one Mapper parameter.
- The binder type must match the annotated parameter type.
- The implementation must be visible, concrete, and have an accessible no-arg constructor.
- Generated execution carries a direct binder reference; there is no global reflective lookup.
- The explicit binder receives the value, including null, and fully replaces default parameter routing for that slot.
- Dynamic SQL carries generated binder slots aligned with emitted parameters. Providers carry either explicit binder metadata or Java/JDBC routing metadata through typed `BoundParameter` values; null values require the overload that supplies the Java type.

## JDBC Type Mappings

A `JdbcTypeMappings` implementation declares one coherent collection of Java-to-JDBC value mappings. A collection may be database-independent or define the complete effective mappings for one database family. Each repeatable `@JdbcTypeMapping` entry identifies one Java type, one `JDBCType`, and one concrete bidirectional `TypeHandler<T>`.

Every package that directly contains a Mapper selects exactly one collection in its own `package-info.java`:

```java
@UseJdbcTypeMappings(ApplicationJdbcTypeMappings.class)
package com.example.user.mapper;

import org.liteorm.annotation.UseJdbcTypeMappings;
```

An application may add one explicit package-scoped override collection without copying the official base collection:

```java
@UseJdbcTypeMappings(
    value = PostgreSqlJdbcTypeMappings.class,
    overrides = ApplicationJdbcTypeMappings.class
)
package com.example.user.mapper;

import org.liteorm.annotation.UseJdbcTypeMappings;
import org.liteorm.types.postgresql.PostgreSqlJdbcTypeMappings;
```

- A mapping collection is a public final class that implements `JdbcTypeMappings`.
- A type handler is public, concrete, independently constructible, and generic for the declared Java type.
- Nested handlers are static and expose a public no-argument constructor.
- Selection is exact-package only. It is not inherited from a parent or child package, and Mapper-interface selection is not supported.
- Source and dependency-supplied collections and handlers receive the same compile-time validation.
- Generated code creates one instance of every effective selected handler and one immutable Mapper-local router. Handlers must therefore be stateless, thread-safe, or externally synchronized.
- For parameters, the router uses the generated Java type and optional placeholder `jdbcType`. Without explicit metadata, the compiler supplies the canonical JDBC representation.
- `TypeHandler.setParameter` receives nullable values and delegates to `setNull` or `setNonNull` by default. A handler may override null binding for a documented driver incompatibility.
- For results, the executor combines the generated Java target type with `ResultSetMetaData`. It resolves each mapped column once per result set and reuses that handler for every row.
- `TypeHandler.getResult` reads one column from the current result row while JDBC resources remain active.
- Duplicate Java-type, JDBC-type, and optional vendor-type-name declarations fail compilation.
- The base and optional override collection are validated independently. An override replaces the exact same Java-type, `JDBCType`, and optional vendor-type-name key or appends a new key. An appended alternative does not change the base collection's inferred parameter `JDBCType`; use placeholder `jdbcType` metadata to select it. Result metadata selects the matching runtime route. More than one override collection fails compilation.
- Generated Mappers expose the selected collection identity through `JdbcTypeMappingsMetadata` without changing their single-`SqlExecutor` constructor.
- Mapping collections are declarative metadata. Router generation and execution use no mutable global registry, discovery, reflection, `ServiceLoader`, or command-line profile.

The compiler does not discover or append another collection. Official database collections therefore declare their complete effective mapping sets while reusing Core handler implementations. Built-in Java types use the router's standard handlers when no selected exact route exists.

PostgreSQL applications can use the official `lite-orm-postgresql-types` artifact and select its collection explicitly:

```java
@UseJdbcTypeMappings(PostgreSqlJdbcTypeMappings.class)
package com.example.postgresql.mapper;

import org.liteorm.annotation.UseJdbcTypeMappings;
import org.liteorm.types.postgresql.PostgreSqlJdbcTypeMappings;
```

The application supplies the PostgreSQL JDBC driver. The artifact does not enable auto-discovery or add a runtime registry. The [Core GA contract](core-contract.md#26-official-postgresql-type-mappings) owns the supported mapping set and exact semantic guarantees.

MySQL applications can use the official `lite-orm-mysql-types` artifact and select its collection explicitly:

```java
@UseJdbcTypeMappings(MySqlJdbcTypeMappings.class)
package com.example.mysql.mapper;

import org.liteorm.annotation.UseJdbcTypeMappings;
import org.liteorm.types.mysql.MySqlJdbcTypeMappings;
```

The application supplies the MySQL JDBC driver. The artifact does not enable auto-discovery or add a runtime registry. The [Core GA contract](core-contract.md#27-official-mysql-type-mappings) owns the supported mapping set and exact semantic guarantees.

## Row Mapper

Use `@UseRowMapper` on a query method with a concrete `RowMapper<T>`.

- Use it for a one-row shape unsupported by scalar, record, or JavaBean generation.
- The mapper generic type must match the method's single result or `List<T>` element type.
- Explicit row mapping takes precedence over and fully bypasses default result type routing.
- A row mapper is a method-level, read-only escape hatch. It does not replace parameter binding or the package's JDBC value mappings for other methods.
- The row mapper runs only after `ResultSet.next()` succeeds.
- Generated code owns one mapper instance and passes a direct reference.

## Execution Interceptor

Register `ExecutionInterceptor` instances through `JdbcAssembly`, or expose them as ordered Spring beans with the starter.

- `beforeExecution` runs in configured order.
- `afterSuccess` and `afterFailure` run after executor-owned cleanup, in reverse order for interceptors whose before callback completed successfully.
- `ExecutionPlan` exposes immutable statement input and `ExecutionOutcome` exposes duration, affected rows, result count, and failure.
- The MVP contract is observational. It does not allow arbitrary SQL replacement or reflective mutation of generated binding and mapping.
- Terminal callback `RuntimeException` values are logged and isolated. They neither mutate the final failure tree nor prevent remaining terminal interceptors from observing the outcome. JVM `Error` values still propagate.
- Any interceptor callback adds runtime work; configure none when the direct path is preferred.

## Routing And Decorators

Prefer a physical or routing `DataSource` behind one explicit Mapper binding. The DataSource and its transaction manager own tenant context, shard selection, read/write routing, physical connection choice, and connection reuse.

Use a `SqlExecutor` decorator only for exceptional whole-execution behavior that cannot be represented by the DataSource or observational interceptor contracts. Such a decorator must preserve statement identity, parameter order, transaction-domain ownership, resource cleanup, and failure suppression. LiteORM does not provide an implicit routing decorator or put DataSource names in `ExecutionPlan`.

## Raw JDBC Boundary

Use raw JDBC when SQL shape, multi-row graph aggregation, vendor APIs, streaming, or resource control cannot fit the generated or typed extension contracts.

Keep this boundary explicit in repository structure and application code. LiteORM does not silently fall back to raw JDBC, runtime XML interpretation, Mapper proxies, or reflection.
