# lite-orm

`lite-orm` is a compile-time-first Java ORM experiment.

The project is not trying to clone MyBatis feature-for-feature. Its core idea is to move work that MyBatis usually performs at runtime into Java compilation:

- mapper implementation generation
- SQL source normalization from annotations and XML
- dynamic SQL rendering code generation
- deterministic parameter binding
- static result mapping
- a thin JDBC runtime pipeline for connection, transaction, statement execution, and result extraction

In short:

> `lite-orm` aims to become a compile-time Mapper platform, not a smaller MyBatis runtime.

## Current Status

The repository currently contains a working core loop:

- `lite-orm-core`: annotation processor, SQL parsers, dynamic SQL AST, FreeMarker code generator, execution plan contract, runtime processor chain, local transaction manager.
- `lite-orm-spring-boot-starter`: Spring Boot auto-configuration, generated Mapper bean registration, and Spring-managed connection/transaction participation.

Recently verified with:

```bash
mvn clean test
```

Result: core `84` tests passed, spring starter `2` tests passed.

## Supported First-Stage Scope

- MyBatis-style `@Mapper` interfaces.
- SQL annotations: `@Select`, `@Insert`, `@Update`, `@Delete`.
- XML-backed mapper methods.
- When one method has both XML SQL and a SQL annotation, XML takes precedence and the compiler emits a warning.
- Common parameter naming: `@Param`, `param1`, `arg0`, `list`, `collection`, `array`.
- Dynamic SQL tags: `if`, `choose`, `when`, `otherwise`, `trim`, `where`, `set`, `foreach`, `sql`, `include`.
- Generated execution plans and static parameter binding.
- Basic static result mapping.
- Local transactions and Spring-managed transaction participation.

## Spring Boot Usage

Generated Mapper implementations can be registered as Spring beans without runtime Mapper proxies. Configure the packages that contain Mapper interfaces and generated `*MapperImpl` classes:

```yaml
lite-orm:
  enabled: true
  mapper-packages:
    - com.example.mapper
```

The starter scans those packages at application startup, registers generated implementations by their Mapper interface type, and injects the configured LiteORM `SqlEngine`. Startup scanning may inspect classes and constructors, but Mapper invocation remains direct Java dispatch with no reflective SQL or result mapping.

The starter uses the application `DataSource`. Inside Spring `@Transactional` boundaries it reuses Spring's transaction-bound connection; outside a Spring transaction each Mapper call uses the DataSource's normal auto-commit behavior and releases JDBC resources after execution.

## SQL Provider Escape Hatch

Use `@UseSqlProvider` only for exceptional SQL that cannot be represented by the supported annotation/XML subset. A provider is a compile-time-known `SqlProvider<P>` with an accessible no-arg constructor and returns immutable `BoundSql` with ordered `BoundParameter` values. Generated code keeps one provider instance and calls it directly; it does not use reflective provider dispatch.

Provider Mapper methods accept zero or one argument. Wrap multiple inputs in a record. A provider method cannot also declare XML or annotation SQL.

## Non-Goals For The First Stage

- Full MyBatis compatibility.
- Arbitrary OGNL support.
- Complex `resultMap` graphs.
- Lazy loading and nested collection aggregation.
- MyBatis plugin compatibility.
- Second-level cache, pagination DSL, sharding, distributed transactions.

Unsupported behavior should fail at compile time with actionable diagnostics instead of falling back to vague runtime behavior.

## Documentation

- Chinese project overview: [README_cn.md](README_cn.md)
- Design philosophy: [Design Philosophy.md](Design%20Philosophy.md)
- Incremental implementation plan: [docs/plans/liteorm-incremental-implementation-plan.md](docs/plans/liteorm-incremental-implementation-plan.md)
- External Maven Mapper example: [lite-orm-examples/basic-mapper/README.md](lite-orm-examples/basic-mapper/README.md)
