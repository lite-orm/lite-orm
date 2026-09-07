# Architecture

LiteORM moves stable Mapper knowledge to compilation and keeps physical JDBC work in one explicit runtime lifecycle.

![LiteORM compile-time and runtime architecture](../assets/liteorm-architecture.svg)

The editable source is stored beside the published image as [`liteorm-architecture.excalidraw`](../assets/liteorm-architecture.excalidraw).

## Compilation

The annotation processor validates Mapper methods, selects SQL sources, compiles supported dynamic SQL, plans parameters and result mapping, and generates ordinary Java implementations. Invalid signatures, unsupported expressions, ambiguous mappings, and malformed XML fail during compilation whenever javac can identify the location.

## Runtime

Generated Mappers build immutable execution plans and call `SqlExecutor`. `JdbcSqlExecutor` owns statement preparation, parameter binding, execution, result reading, cleanup, and final observation. Runtime code does not load Mapper XML, evaluate OGNL, dispatch through Mapper proxies, or discover type handlers. Generated immutable routes select handlers from declared Java types and JDBC metadata.

## Host Integrations

Standalone and Spring applications use the same generated Mapper and `JdbcSqlExecutor` path. They differ only in assembly, connection participation, transaction ownership, and Mapper instance registration.

- [Standalone JDBC](core/standalone.md) uses `JdbcAssembly` and callback transactions.
- [Spring Boot](spring/spring-boot.md) owns IoC, named DataSource binding, and transaction boundaries.

One Mapper belongs to one DataSource domain. Applications with several DataSources use disjoint Mapper packages and independent executor graphs.

![Animated comparison of the LiteORM and MyBatis lifecycles](../assets/liteorm-vs-mybatis-flow-en.gif)

The durable architectural principles and non-goals are defined in [Design Philosophy](../../Design-Philosophy.md). Normative behavior is defined in the [Core contract](../reference/core-contract.md).
