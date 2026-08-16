# lite-orm

`lite-orm` is a compile-time-first Java ORM experiment.

The project is not trying to clone MyBatis feature-for-feature. Its core idea is to move work that MyBatis usually performs at runtime into Java compilation:

- mapper implementation generation
- SQL source normalization from annotations and XML
- dynamic SQL rendering code generation
- deterministic parameter binding
- static result mapping
- a thin JDBC runtime rebuilt on explicit execution and transaction roles

In short:

> `lite-orm` aims to become a compile-time Mapper platform, not a smaller MyBatis runtime.

## Current Status

The repository currently contains a working core loop:

- `lite-orm-core`: annotation processor, SQL parsers, dynamic SQL AST, generated Mapper source, execution-plan contracts, and the current JDBC runtime.
- `lite-orm-spring-boot-starter`: Spring Boot auto-configuration, generated Mapper bean registration, and Spring-managed connection/transaction participation.

The compile-time and runtime loops are working. The obsolete engine, processor chain, mutable execution context, connection-provider/coordinator abstractions, and global runtime configuration have been deleted. Generated Mappers depend only on `SqlExecutor`; core provides the fixed JDBC executor and Spring participates through its transaction adapter.

Recently verified with:

```bash
mvn clean test
```

The full reactor includes compiler diagnostics, generated-source assertions, Spring integration, and external H2 Mapper fixtures.

## Supported First-Stage Scope

- LiteORM-owned annotations under `org.liteorm.annotation`; the project does not publish classes under the MyBatis/iBatis namespace.
- MyBatis-style Mapper interfaces declared with `org.liteorm.annotation.Mapper`.
- SQL annotations: `@Select`, `@Insert`, `@Update`, `@Delete`.
- XML-backed mapper methods.
- When one method has both XML SQL and a SQL annotation, XML takes precedence and the compiler emits a warning.
- Common parameter naming: `@Param`, `param1`, `arg0`, `list`, `collection`, `array`.
- Dynamic SQL tags: `if`, `choose`, `when`, `otherwise`, `trim`, `where`, `set`, `foreach`, `sql`, `include`.
- A controlled OGNL-like expression subset for dynamic SQL. The annotation processor translates it directly into native Java conditions, property access, loops, and bind expressions; LiteORM uses no OGNL, MVEL, SpEL, or other expression engine at compile time or runtime.
- Generated execution plans and static parameter binding.
- Basic static result mapping.
- Local transactions and Spring-managed transaction participation.

Typical imports are:

```java
import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.Select;
```

## Spring Boot Usage

Generated Mapper implementations can be registered as Spring beans without runtime Mapper proxies. Configure the packages that contain Mapper interfaces and generated `*MapperImpl` classes:

```yaml
lite-orm:
  enabled: true
  mapper-bindings:
    - package-name: com.example.mapper
      data-source: dataSource
```

The starter scans each explicitly configured package at application startup, resolves the named Spring `DataSource`, assembles one `SqlExecutor`, and registers each generated implementation once under the decapitalized Mapper interface name. Generated implementations remain plain Java classes without Spring component annotations. Startup scanning may inspect classes and constructors, but Mapper invocation remains direct Java dispatch with no reflective SQL or result mapping.

Every Mapper package and Mapper interface belongs to exactly one DataSource domain. Package bindings are always explicit, including applications with a single DataSource; the starter does not infer a default DataSource or Mapper scan package.

The target runtime gives each generated Mapper one `SqlExecutor`. A `JdbcSqlExecutor` is bound to one `TransactionFactory` and therefore one DataSource/transaction domain. Standalone transactions use core `SimpleTransaction` semantics; Spring uses a `SpringTransaction` adapter and continues to control transaction boundary timing.

Applications with multiple DataSources use disjoint Mapper package bindings and independent executor graphs. The same Mapper interface is not registered against multiple DataSources. LiteORM does not hide DataSource selection inside an execution plan and does not provide distributed commit in core. Dynamic tenant, shard, or read/write routing belongs to an application-provided routing DataSource behind one explicit binding.

## SQL Provider Escape Hatch

Use `@UseSqlProvider` only for exceptional SQL that cannot be represented by the supported annotation/XML subset. A provider is a compile-time-known `SqlProvider<P>` with an accessible no-arg constructor and returns immutable `BoundSql` with ordered `BoundParameter` values. Generated code keeps one provider instance and calls it directly; it does not use reflective provider dispatch.

Provider Mapper methods accept zero or one argument. Wrap multiple inputs in a record. A provider method cannot also declare XML or annotation SQL.

## Custom JDBC Adapters

Use `@UseParameterBinder` on an exceptional Mapper parameter when JDBC's default `setObject` conversion is insufficient. Use `@UseRowMapper` on a query method when the result shape cannot be generated by LiteORM's built-in scalar, record, or JavaBean mapping.

Both adapters are typed interfaces with compile-time-known implementation classes and accessible no-arg constructors. Generated Mapper implementations keep one adapter instance and pass direct references through the execution plan. Explicit adapters take precedence over built-in conversion. A null parameter bypasses the custom binder and is bound as SQL `NULL`; a row mapper is called only after `ResultSet.next()` succeeds.

## Execution Interceptors

Register `ExecutionInterceptor` instances around the future narrow JDBC execution boundary for logging, metrics, auditing, or authorization. `beforeExecution` receives the immutable `ExecutionPlan`; terminal callbacks receive an immutable `ExecutionOutcome`. DataSource routing uses dedicated typed roles rather than generic interceptor metadata.

`beforeExecution` runs in configured order. `afterSuccess` and `afterFailure` unwind in reverse order. Spring Boot collects interceptor beans using Spring ordering. Callback failures still allow JDBC resources to close; failure callback exceptions are attached to the original execution failure as suppressed exceptions.

## Non-Goals For The First Stage

- Full MyBatis compatibility.
- Arbitrary OGNL support or expression-engine evaluation. Unsupported expressions fail compilation instead of falling back to interpretation.
- Complex `resultMap` graphs.
- Lazy loading and nested collection aggregation.
- MyBatis plugin compatibility.
- Second-level cache, pagination DSL, sharding, distributed transactions.

Unsupported behavior should fail at compile time with actionable diagnostics instead of falling back to vague runtime behavior.

## Documentation

- Chinese project overview: [README_cn.md](README_cn.md)
- Design philosophy: [Design Philosophy.md](Design%20Philosophy.md)
- Active runtime architecture plan: [docs/plans/liteorm-runtime-architecture-implementation-plan.md](docs/plans/liteorm-runtime-architecture-implementation-plan.md)
- MyBatis compatibility matrix: [docs/mybatis-compatibility.md](docs/mybatis-compatibility.md)
- Migration guide: [docs/migration-guide.md](docs/migration-guide.md)
- Extension contracts: [docs/extensions.md](docs/extensions.md)
- External Maven Mapper example: [lite-orm-examples/basic-mapper/README.md](lite-orm-examples/basic-mapper/README.md)
