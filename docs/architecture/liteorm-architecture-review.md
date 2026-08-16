# LiteORM Architecture Review

Date: 2026-08-16

This document describes the current architecture after the runtime refactor. It intentionally excludes the deleted `SqlEngine`, processor-chain, mutable execution-context, `ConnectionProvider`, and `TransactionCoordinator` designs.

## Architectural Goal

LiteORM is a compile-time Mapper platform. SQL source selection, supported dynamic expressions, ordered parameters, binder selection, and result mapping become ordinary Java during annotation processing. Runtime code owns only the fixed JDBC lifecycle and explicitly typed extension calls.

The desired path is readable without historical compatibility knowledge:

```text
Mapper interface + annotations/XML
        |
        v
LiteOrmProcessor -> CompilePipeline -> MapperCompilationModel
        |
        v
Generated *MapperImpl
        |
        v
SqlExecutor.execute(ExecutionPlan)
        |
        v
JdbcSqlExecutor -> TransactionFactory -> Transaction -> JDBC
```

## Compile-Time Flow

1. `LiteOrmProcessor` discovers Mapper interfaces.
2. `CompilePipeline` resolves XML-over-annotation precedence, validates supported signatures and XML, and creates one `MapperCompilationModel`.
3. Dynamic SQL is represented as a compile-time AST and rendered as native Java conditions, scopes, and loops.
4. `FreemarkerCodeGenerator` emits a concrete `*MapperImpl` with stable source comments, statement IDs, ordered parameters, and direct result mapping.
5. Unsupported behavior fails compilation with diagnostics attached to the Mapper method when javac exposes a language element.

Generated source has no runtime Mapper proxy, runtime XML parser, runtime expression engine, reflective provider invocation, reflective parameter lookup, or reflective result mapping.

## Runtime Flow

`JdbcSqlExecutor` owns one fixed sequence:

```text
validate plan
  -> interceptor before callbacks
  -> open or join Transaction
  -> obtain Connection
  -> prepare PreparedStatement
  -> bind ordered parameters
  -> execute query/update/batch
  -> extract rows/update count/generated key
  -> interceptor success or failure callbacks
  -> close ResultSet
  -> close PreparedStatement
  -> close/release Transaction
```

These phases are not a responsibility chain and are not reorderable plugins. `ExecutionInterceptor` is the narrow observational before/after extension point.

## Execution And Transaction Roles

| Role | Responsibility | Ownership |
| --- | --- | --- |
| `SqlExecutor` | Mapper-facing execution contract | Core public API |
| `JdbcSqlExecutor` | Fixed JDBC lifecycle | Core runtime |
| `ExecutionPlan` / `BatchExecutionPlan` | Immutable statement input | Generated code and core API |
| `SqlResult` | JDBC-neutral execution result consumed by generated Mapper code | Core API |
| `TransactionFactory` | Creates one execution-scoped transaction handle | Host-specific strategy |
| `Transaction` | Connection acquisition, participation, completion, timeout, and release semantics | One execution handle |
| `TransactionalExecutor` | Explicit standalone transaction callback boundary | Core API |
| `SimpleTransactionFactory` | Temporary auto-commit handles or participation in the current local root transaction | Standalone core implementation |
| `SpringTransactionFactory` | Spring thread-bound connection participation | Spring starter implementation |

`JdbcSqlExecutor` never asks whether a connection is standalone or Spring-owned. It depends only on `TransactionFactory` and `Transaction`.

## Standalone Assembly

`LiteOrm.jdbc(dataSource).build()` creates one immutable `JdbcAssembly` containing:

- one Mapper-facing `SqlExecutor`;
- one `TransactionalExecutor` sharing the same `SimpleTransactionFactory`;
- one transaction domain and domain guard;
- an immutable ordered interceptor list.

Calls outside a transaction callback receive temporary auto-commit handles. A callback binds one root `SimpleTransaction` to the current thread. Mapper calls in that callback receive participating handles, and nested callbacks join the same root.

## Spring Assembly

The starter requires explicit package-to-DataSource bindings. For each named DataSource it registers one Spring-aware `SqlExecutor`; each generated Mapper is registered once under the JavaBeans-decapped interface name and receives that executor through its constructor.

`SpringTransaction` obtains and releases connections through `DataSourceUtils`. Its `commit` and `rollback` methods are intentionally empty because `PlatformTransactionManager` owns boundary timing. An active Spring transaction that is not bound to the configured DataSource fails with a transaction-domain mismatch.

Generated Mapper classes remain independent of Spring annotations and APIs.

## Multiple DataSources

One Mapper interface belongs to one DataSource domain. Multiple application DataSources require disjoint Mapper package bindings and independent executor graphs.

Core does not:

- put DataSource names in `ExecutionPlan`;
- register the same Mapper against several DataSources;
- coordinate commits across assemblies;
- provide distributed transactions;
- own tenant or shard routing context.

A physical or routing DataSource may sit behind one binding. The DataSource and matching transaction manager own physical connection selection. A whole-`SqlExecutor` decorator is reserved for exceptional routing that cannot be expressed by the DataSource itself.

## Extension Boundary

The preferred order is:

1. generated annotation/XML SQL;
2. typed `SqlProvider`;
3. typed `ParameterBinder`;
4. typed `RowMapper`;
5. observational `ExecutionInterceptor`;
6. exceptional `SqlExecutor` decorator;
7. explicit raw JDBC.

Provider, binder, row-mapper, and interceptor instances are reused and therefore must be stateless, thread-safe, or externally synchronized.

## Dependency Direction

| Package/module | May depend on | Must not depend on |
| --- | --- | --- |
| `org.liteorm.annotation` | Java annotation types | Compiler and runtime implementations |
| `org.liteorm.compile` | Annotation-processing APIs and public generated-code contracts | Spring |
| `org.liteorm.api` | JDK/JDBC contracts | Compiler or Spring implementations |
| Core JDBC/transaction implementations | `org.liteorm.api` | Compiler models and Spring |
| Spring starter | Core public API and Spring | Compiler internals |
| Generated Mapper source | Mapper types and narrow core API | Spring, compiler internals, runtime reflection |

## Current Review Result

- Generated Mappers depend on one stable `SqlExecutor` contract.
- Fixed JDBC phases are explicit and non-reorderable.
- Standalone and Spring connection ownership are isolated behind transaction strategies.
- Multiple DataSources are application assembly, not statement metadata.
- Cross-cutting observation uses interceptors rather than fixed-phase processors.
- Generated source is stable, readable, and covered by golden-source and compiler-diagnostic tests.

The remaining architecture review work is to evaluate SOLID boundaries, justified design patterns, public API size, exception taxonomy, concurrency guarantees, and optimization gates in the final R9.2 review.
