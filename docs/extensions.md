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
- Starter uses `SpringTransactionFactory` and core `JdbcAssembly` to create the executor.
- Parent and child package rules cannot overlap.
- The same package may be bound more than once only when distinct `bean-name-prefix` values produce distinct Mapper bean names.
- Binding happens during application startup. Mapper invocation still calls its final executor field directly and performs no package or bean lookup.
- Each Spring transaction boundary must use the `PlatformTransactionManager` associated with the same DataSource as the selected executor.

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
- `afterSuccess` and `afterFailure` run in reverse order for interceptors whose before callback was entered.
- `ExecutionPlan` exposes immutable statement input and `ExecutionOutcome` exposes duration, affected rows, result count, and failure.
- The MVP contract is observational. It does not allow arbitrary SQL replacement or reflective mutation of generated binding and mapping.
- Failure callbacks cannot prevent resource cleanup. Their exceptions are suppressed onto the original execution failure.
- Any interceptor callback adds runtime work; configure none when the direct path is preferred.

## Raw JDBC Boundary

Use raw JDBC when SQL shape, multi-row graph aggregation, vendor APIs, streaming, or resource control cannot fit the generated or typed extension contracts.

Keep this boundary explicit in repository structure and application code. LiteORM does not silently fall back to raw JDBC, runtime XML interpretation, Mapper proxies, or reflection.
