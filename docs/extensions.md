# Extension Contracts

LiteORM keeps generated Mapper code as the default. Extensions are narrow, explicit escape hatches for cases that cannot remain fully generated.

## Guarantee Levels

| Path | Compile-time guarantees | Runtime work |
| --- | --- | --- |
| Generated annotations/XML | Strongest: SQL source, supported expressions, parameter order, and built-in mapping are generated | JDBC execution only |
| Typed extension | Extension class, generic compatibility, visibility, constructor, and invocation are compile-time-bound | Explicit provider, binder, mapper, or interceptor code runs |
| Raw JDBC | Outside LiteORM generation guarantees | Application owns SQL, binding, mapping, resources, and diagnostics |

## Concurrency Contract

Generated Mapper implementations and `JdbcSqlExecutor` are designed for concurrent reuse. Each Mapper call builds its own immutable execution plan, while simple transaction state and Spring transaction-bound connections remain isolated by thread.

Generated code keeps one Provider, Binder, and RowMapper instance per Mapper instance. `JdbcSqlExecutor` also reuses its configured Interceptor instances, and Spring normally supplies those interceptors as singleton beans. Therefore every Provider, Binder, RowMapper, and Interceptor implementation must be stateless, thread-safe, or protect its mutable state with external synchronization. LiteORM does not clone extension instances per call and currently provides no stateful-adapter factory contract.

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

- Use it when JDBC `setObject` conversion is insufficient for one Java value type.
- The adapter type must match the annotated parameter type.
- The implementation must be visible, concrete, and have an accessible no-arg constructor.
- Generated execution carries a direct binder reference; there is no global reflective type-handler lookup.
- Null values bypass the custom binder and bind SQL `NULL`.
- Dynamic SQL carries generated binder slots aligned with emitted parameters. Providers carry binder metadata through typed `BoundParameter` values.

## Row Mapper

Use `@UseRowMapper` on a query method with a concrete `RowMapper<T>`.

- Use it for a one-row shape unsupported by scalar, record, or JavaBean generation.
- The mapper generic type must match the method's single result or `List<T>` element type.
- Explicit row mapping takes precedence over built-in mapping.
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
