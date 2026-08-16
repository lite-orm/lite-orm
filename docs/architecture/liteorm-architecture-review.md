# LiteORM Final Architecture Review

Date: 2026-08-16

## Executive Result

LiteORM now has one understandable dependency path from user Mapper source to JDBC:

```text
Mapper interface + annotations/XML
        |
        v
LiteOrmProcessor
        |
        v
CompilePipeline -> MapperCompilationModel -> FreemarkerCodeGenerator
        |
        v
Generated *MapperImpl
        |
        v
SqlExecutor.execute(ExecutionPlan)
        |
        v
JdbcSqlExecutor -> ConnectionHandleFactory -> ConnectionHandle -> JDBC
```

The architecture no longer depends on the deleted `SqlEngine`, fixed-phase processor chain, mutable execution context, `ConnectionProvider`, `TransactionCoordinator`, global configuration singleton, runtime Mapper proxy, runtime XML parser, or runtime expression engine.

The principal design and Core GA contract are accepted. Correctness, API-hardening, production-database, and concurrency gates are complete; performance work remains gated on reproducible measurement.

## End-To-End Review

### Compile-Time Input

User input consists of:

- LiteORM Mapper annotations;
- Mapper method signatures;
- optional Mapper XML resources;
- typed provider, binder, and row-mapper declarations.

`LiteOrmProcessor` is the annotation-processing entry point. XML has method-scoped precedence over SQL annotations and produces a compiler warning when both define the same statement.

Review result: **accepted**. Input ownership is explicit and unsupported behavior fails compilation instead of falling back to runtime interpretation.

### Compile-Time Model

`CompilePipeline` validates signatures, resolves SQL sources, builds the dynamic SQL AST, determines ordered parameters and adapters, and produces `MapperCompilationModel`.

The compiler package is implementation code. `LiteOrmProcessor` is the only public compiler type; `CompilePipeline`, parser implementations, AST nodes, compilation models, and code generators are package-private and are not application extension APIs.

Review result: **accepted**. A future processor artifact split may improve dependency packaging, but it is not required for API correctness.

### Source Generation

`FreemarkerCodeGenerator` emits concrete Mapper implementations with:

- one constructor dependency: `SqlExecutor`;
- stable explicit imports;
- Mapper method and SQL-source comments;
- statement IDs;
- native Java dynamic conditions and loops;
- ordered parameter arrays or lists;
- direct provider, binder, and row-mapper references;
- direct scalar, record, and JavaBean mapping.

Review result: **accepted**. Golden-source tests cover annotation and XML rendering, and focused compiler fixtures cover provider, binder, row mapper, batch, generated keys, diagnostics, and unsupported behavior.

### Runtime Execution

`JdbcSqlExecutor` owns one fixed sequence:

```text
validate
  -> before interceptors
  -> open or join transaction handle
  -> acquire connection
  -> prepare statement
  -> bind ordered parameters
  -> execute
  -> extract result
  -> terminal interceptors
  -> close ResultSet
  -> close PreparedStatement
  -> close/release transaction handle
```

Cleanup failures are preserved, earlier SQL failures remain primary, and interceptor failure callbacks are suppressed onto the earlier failure.

Review result: **accepted**. This sequence is mandatory lifecycle code, not a Chain of Responsibility.

### Standalone Assembly

`LiteOrm.jdbc(dataSource)` returns the only assembly builder. `JdbcAssembly` contains one `SqlExecutor` and one `TransactionalExecutor` sharing one `SimpleConnectionHandleFactory`.

- Calls outside a callback use temporary auto-commit handles.
- A callback binds one root `SimpleTransaction` to the current thread.
- Mapper calls inside the callback receive participating handles.
- Nested callbacks join the root.
- The outer callback owns commit, rollback, cleanup, and thread-local removal.

Review result: **accepted**. The builder is justified because assembly has optional domain and interceptor configuration; generated execution plans do not use a builder.

### Spring Assembly

The starter requires explicit `mapper-bindings`. `GeneratedMapperBeanDefinitionRegistrar` scans configured packages, resolves the named DataSource, registers one Spring-aware executor per DataSource, and registers each generated Mapper once under the JavaBeans-decapped interface name.

`SpringConnectionHandleFactory` adapts executor connection participation to `DataSourceUtils`. `SpringConnectionHandle` participates in Spring connection ownership and has no commit/rollback authority because `PlatformTransactionManager` owns boundary timing.

Review result: **accepted**. Core has no Spring dependency and generated Mapper classes remain Spring-neutral.

### Multiple DataSources

Multiple DataSources are represented by independent assembly graphs:

- one Mapper interface is registered once;
- one Mapper package binding names one physical or routing DataSource bean;
- package bindings are disjoint;
- each executor remains permanently associated with one transaction domain;
- the same Mapper is not rebound to several DataSources;
- core does not coordinate distributed commit.

Review result: **accepted**. DataSource selection is application assembly, not statement metadata.

## SOLID Review

### Single Responsibility Principle

Accepted boundaries:

- `LiteOrmProcessor`: annotation-processing entry point;
- `CompilePipeline`: compiler orchestration and validation;
- `FreemarkerCodeGenerator`: Java source rendering;
- generated Mapper: statement construction and typed return mapping;
- `JdbcSqlExecutor`: fixed physical JDBC lifecycle;
- `ConnectionHandleFactory`: execution-scoped connection participation strategy;
- `ConnectionHandle`: one execution's connection participation and release semantics;
- `TransactionalExecutor`: explicit transaction boundary;
- registrar: Spring bean-definition assembly.

Follow-up: `CompilePipeline` remains the largest compiler class. Split only when a concrete validation/modeling responsibility can be extracted with focused tests; do not create ceremonial layers.

### Open/Closed Principle

Accepted extension points are typed and narrow:

- `SqlProvider` for exceptional SQL structure;
- `ParameterBinder` for one value type;
- `RowMapper` for one row shape;
- `ExecutionInterceptor` for observation;
- `ConnectionHandleFactory` for host connection participation;
- optional `SqlExecutor` decoration for exceptional whole-execution routing.

Fixed JDBC phases are intentionally closed to reordering and replacement. New behavior should use a typed extension or change the executor implementation with lifecycle tests.

### Liskov Substitution Principle

`SimpleTransaction` and `SpringConnectionHandle` satisfy the same executor-facing `ConnectionHandle` contract because the executor requires only connection access and close/release behavior. Commit and rollback authority remains inside the local transactional executor or the host transaction manager.

### Interface Segregation Principle

Strong small interfaces:

- `SqlExecutor` has one operation;
- `ConnectionHandleFactory` has one creation method;
- `TransactionalExecutor` has one callback method;
- provider, binder, row mapper, and domain guard are focused contracts.

`ConnectionHandle` intentionally exposes no commit or rollback methods. `JdbcAssembly.Builder.domainGuard(...)` is a standalone-assembly customization using `SimpleTransactionDomainGuard`; it is not advertised as a host transaction-manager SPI.

### Dependency Inversion Principle

Accepted dependency direction:

```text
generated Mapper -> org.liteorm.api
JdbcSqlExecutor -> ConnectionHandleFactory / ConnectionHandle
standalone transaction implementation -> org.liteorm.api + JDBC
Spring transaction implementation -> org.liteorm.api + Spring JDBC
Spring registrar -> core public assembly contracts
```

Core runtime does not depend on compiler models or Spring. Spring depends on core contracts rather than core depending on Spring callbacks.

## Design Pattern Review

### Strategy

Justified for `ConnectionHandleFactory` and `ConnectionHandle` implementations. Standalone and Spring have genuinely different connection ownership semantics behind the same executor dependency.

### Factory

Justified for `ConnectionHandleFactory.openHandle()`. Each SQL execution needs a fresh ownership/participation handle even when it joins an existing root transaction.

### Explicit Lifecycle / Template

The fixed sequence inside `JdbcSqlExecutor` is template-like, but it is intentionally not an inheritance-based Template Method. The lifecycle is explicit code because subclasses must not reorder resource ownership phases.

### Interceptor

Justified for before/after observation. Logging, slow-query reporting, audit, metrics, tracing, and authorization can observe immutable plan/outcome data without replacing fixed phases.

The executor reports only JDBC certainty (`NOT_EXECUTED`, `OUTCOME_UNKNOWN`, or `EXECUTED`). It does not claim commit or rollback because final transaction completion may belong to Spring or another host manager.

`beforeExecution` may veto before JDBC work begins. `afterSuccess` and `afterFailure` are terminal observation callbacks: their runtime failures are logged through the JDK logger, do not alter SQL results or primary failures, and do not prevent remaining observers from running.

### Adapter

Justified for `SpringConnectionHandleFactory` and `SpringConnectionHandle`, which adapt Spring JDBC connection participation to the core connection contracts.

### Builder

Justified only for `LiteOrm.jdbc(dataSource)` assembly because domain and interceptor options are optional. Builders are not used for execution plans generated on hot paths.

### Decorator

Allowed only for optional whole-`SqlExecutor` routing that cannot be represented by a routing DataSource. No implicit routing decorator is part of core.

### Rejected: Chain Of Responsibility

Connection acquisition, preparation, binding, execution, extraction, and cleanup have hard dependencies and one valid order. Presenting them as independently reorderable handlers weakens correctness and hides ownership. They remain explicit inside `JdbcSqlExecutor`.

## Public API Review

### Accepted Stable Surface

- annotations under `org.liteorm.annotation`;
- generated-code contracts under `org.liteorm.api`;
- `LiteOrm` and `JdbcAssembly` for standalone assembly;
- typed interceptors and adapters;
- Spring configuration properties and auto-configuration entry points.

The supported core surface is guarded by an executable class-list test. `LiteOrmProcessor` is the only public compiler type; parser, AST, model, and generator implementations are package-private.

### Surface Reduction Candidates

- `SqlResult` exposes only explicit factories such as `forQuery` and `forUpdate`; pre-release compatibility aliases were removed.
- `JdbcSqlExecutor`, `SimpleConnectionHandleFactory`, and `SimpleTransactionalExecutor` may not all need to remain direct user construction APIs once assembly is established.
- `SpringConnectionHandle` has a package-private constructor and may not need a public type.
- `domainGuard(...)` exposes a concrete implementation rather than a complete abstraction.

Do not remove these in the documentation review. Handle them as separately tested compatibility changes.

## Package Naming Review

- `org.liteorm.annotation`: user compiler input, clear.
- `org.liteorm.api`: generated/runtime shared contracts, clear but should stay small.
- `org.liteorm.compile`: processor implementation; only `LiteOrmProcessor` is public.
- `org.liteorm.jdbc`: physical JDBC executor, clear.
- `org.liteorm.transaction`: standalone transaction implementation, clear.
- `org.liteorm.interceptor`: provided observation implementations, clear.
- `org.liteorm.runtime`: currently contains only result conversion helpers; consider moving or renaming only if a broader runtime package taxonomy emerges.

Review result: **acceptable**. Avoid package churn without a compatibility or discoverability benefit.

## Exception Taxonomy Review

Accepted categories:

- `ConfigurationException` for assembly/provider contract failures;
- `SqlExecutionException` for physical execution failure with statement context;
- `TransactionException` for begin/commit/rollback/cleanup/domain failures;
- compile diagnostics for unsupported Mapper behavior.

Plan-construction value objects use `IllegalArgumentException` for direct programmer contract violations. Framework runtime failures use the LiteORM hierarchy. Local transaction failures describe only implemented begin, commit, rollback, rollback-only, cleanup, and DataSource-domain behavior; statement timeout and database deadlock remain JDBC execution failures.

Default failure messages redact SQL text, parameters, row arrays, and configuration values. `SqlExecutionException.Diagnostics` exposes SQL only through an explicit diagnostic accessor.

## Concurrency And Immutability Review

- Generated Mapper fields are final and adapter instances are reused.
- `JdbcSqlExecutor` copies its interceptor list and keeps no per-call mutable state in fields.
- execution-local resources live in method locals.
- `SimpleConnectionHandleFactory` and `SimpleTransactionDomainGuard` isolate active transaction state with instance-scoped `ThreadLocal` values.
- Spring connection state is delegated to Spring's thread-bound transaction synchronization.
- the XML compiler cache is an instance-scoped `ConcurrentHashMap`, not a global mutable cache.
- `ExecutionPlan` clones parameter and binder arrays; `BatchExecutionPlan` clones row arrays.
- `BoundSql` copies the parameter list.
- `SqlResult` defensively copies query rows and batch update counts on input and output.

Extension instances must be stateless, thread-safe, or externally synchronized. LiteORM does not clone provider, binder, row-mapper, or interceptor instances per call.

## Production Database Compatibility

Pinned PostgreSQL 16.4 and MySQL 8.4.0 Testcontainers suites execute one shared contract for generated Mapper scalar/Record/JavaBean mapping, dynamic SQL, local commit/rollback/rollback-only behavior, JDBC batch, statement timeout, temporal values, driver-neutral identifiers, binary values, cursor consumption, and generated keys. Vendor differences stay in fixture DDL and sleep SQL. Generated-key columns are explicit because PostgreSQL otherwise returns the complete inserted row for `RETURN_GENERATED_KEYS`.

## Test Readability Review

Strong suites:

- focused compiler diagnostics and generated-source golden tests;
- JDBC lifecycle and cleanup-order tests;
- standalone transaction and concurrency characterization;
- Spring transaction participation and package-binding tests;
- external Maven processor fixture;
- executable H2 standalone example.

Legacy narrative-only edge tests were removed, and generated-source demonstrations now contain executable assertions.

Proxy-based JDBC characterization tests are verbose but valuable because they assert physical ordering and suppression semantics without relying on a database driver's incidental behavior.

## Generated-Source Readability Review

Accepted properties:

- deterministic header without timestamps;
- explicit imports;
- Mapper method and SQL-source comments;
- descriptive execution/result variables;
- scoped names for `foreach`, `choose`, `where`, `set`, and `trim`;
- statement IDs in plans and exceptions;
- direct typed mapping and adapter calls;
- diagnostics attached to Mapper methods.

XML resources cannot be attached to javac as language-model elements. XML failures therefore navigate to the corresponding Mapper method while retaining statement/tag context in the diagnostic message.

## Correctness Gate Before Optimization

The pre-optimization correctness gate is complete:

1. minimal local transaction options and rollback-only semantics are characterized;
2. compiler implementation helpers are internalized and the public API surface is executable-tested;
3. runtime exceptions share the LiteORM hierarchy and default messages follow redaction rules;
4. `SqlResult` query rows and batch counts are defensively copied;
5. narrative print tests were removed or replaced with focused assertions;
6. PostgreSQL/MySQL compatibility and bounded multi-DataSource concurrency are verified.

Do not add caches or hot-path complexity without the benchmark gate below.

## Optimization Gate

No performance change is approved without a reproducible benchmark. Future measurements must include:

- scalar query;
- record mapping;
- JavaBean mapping;
- dynamic SQL;
- JDBC batch;
- generated keys;
- interceptor overhead;
- standalone transaction callback;
- Spring transaction participation;
- comparison with direct JDBC and a documented MyBatis baseline.

Measure allocations and throughput before changing generated lists, plan copies, row arrays, interceptor tracking, or compiler caches. Keep any accepted cache immutable or instance-scoped.

## Final Decision

The architecture is understandable from component names and dependency direction:

- compilation owns interpretation and generation;
- generated Mappers own statement construction and typed mapping;
- `SqlExecutor` owns physical execution;
- transaction strategies own connection participation;
- assembly owns DataSource selection;
- interceptors own observation;
- applications own distributed transactions and routing infrastructure.

Proceed to optimization only through the reproducible benchmark gate. The published support boundary is `docs/core-ga-contract.md`.
