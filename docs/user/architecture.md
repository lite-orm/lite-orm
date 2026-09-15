---
title: Compile-time Java ORM Architecture
description: See how Lynxus compiles SQL mapping into Java and keeps JDBC execution explicit at runtime.
---

# Architecture

Lynxus moves stable Mapper knowledge to compilation and keeps physical JDBC work in one explicit runtime lifecycle.

![Lynxus compile-time and runtime architecture](../assets/lynxus-architecture.svg)

The editable source is stored beside the published image as [`lynxus-architecture.excalidraw`](../assets/lynxus-architecture.excalidraw).

## Compilation

The `lynxus-processor` artifact validates Mapper methods, selects SQL sources, compiles supported
dynamic SQL, plans parameters and result mapping, and generates ordinary Java implementations.
`lynxus-core` is the runtime artifact consumed by generated source. Generated dynamic methods keep
native Java branches and loops visible while using one `BoundSqlBuilder` to own SQL spacing, clause
normalization, and aligned placeholder routing. The builder is a generated-code/runtime seam rather
than an application query DSL. Invalid signatures, unsupported expressions, ambiguous mappings, and
malformed XML fail during compilation whenever javac can identify the location.

## Runtime

Generated Mappers retain immutable statement definitions and bind only each invocation's SQL parameter values into execution plans before calling `SqlExecutor`. Fixed SQL, statement identity, options, JDBC routing, and result assembly live in generated `QueryDefinition`, `CommandDefinition`, or `BatchDefinition` fields instead of being rebuilt on every call. Standard SELECT methods use a typed `QueryExecutionPlan<T>` whose generated result assembler performs direct object construction and setter calls after Core JDBC value conversion. `JdbcSqlExecutor` owns statement preparation, parameter binding, execution, result reading and assembly, cleanup, and final observation. Runtime code does not load Mapper XML, evaluate OGNL, dispatch through Mapper proxies, or discover handlers.

This generated-code boundary is also the basis for Lynxus's AOT-first
position. The standalone path is verified with an ARM64 Native Image smoke
build; see [AOT usage](aot.md).

## Host Integrations

Standalone and Spring applications use the same generated Mapper and `JdbcSqlExecutor` path. They differ only in assembly, connection participation, transaction ownership, and Mapper instance registration.

- [Standalone JDBC](core/standalone.md) uses `JdbcAssembly` and callback transactions.
- [Spring Boot](spring/spring-boot.md) owns IoC, named DataSource binding, and transaction boundaries.

One Mapper belongs to one DataSource domain. Applications with several DataSources use disjoint Mapper packages and independent executor graphs.

![Animated comparison of the Lynxus and traditional ORM lifecycles](../assets/lynxus-vs-traditional-orm-flow-en.gif)

The durable architectural principles and non-goals are defined in [Design Philosophy](../../Design-Philosophy.md). Normative behavior is defined in the [Core contract](../reference/core-contract.md).
