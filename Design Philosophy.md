# LiteORM Design Philosophy

## Purpose

LiteORM exists to provide a smaller and more predictable SQL Mapper for Java teams that value explicit SQL, compile-time feedback, readable generated code, and direct JDBC behavior.

The project does not measure success by copying the MyBatis API surface. It succeeds when a team can adopt LiteORM through normal Maven or Gradle dependencies, migrate common Mapper code with limited friction, understand generated behavior, and diagnose failures without framework internals.

## Core Model

The durable LiteORM model is:

```text
Mapper interface + annotations/XML
        |
        v
Annotation processor
        |
        v
Validated compilation model
        |
        v
Generated Mapper implementation
        |
        v
SqlExecutor
        |
        v
Fixed JDBC lifecycle
```

Compile-time work includes:

- Mapper method validation;
- SQL source selection;
- dynamic SQL expression compilation;
- parameter and adapter planning;
- return-shape and result-mapping validation;
- readable Java source generation.

Runtime work includes:

- acquiring a connection handle;
- preparing and configuring a JDBC statement;
- binding parameters;
- executing SQL;
- reading or mapping results;
- closing resources;
- publishing one final execution outcome.

## Architectural Principles

### Compile Stable Knowledge

Anything that can be determined reliably by javac should not be rediscovered on every Mapper call. Unsupported signatures, unknown parameters, invalid XML, unsafe substitution, incompatible result types, and unsupported dynamic expressions should fail during compilation.

Compile-time processing must remain deterministic. The processor must not depend on network resources, runtime container state, or a general template engine.

### Keep Runtime Explicit

Generated Mapper implementations are ordinary Java classes. They receive a `SqlExecutor`, build immutable execution plans, execute them, and adapt the result to the declared return type.

LiteORM does not expose a session abstraction and does not use runtime Mapper proxies. The runtime path should remain visible in generated source and debuggable with normal Java tools.

### Keep One JDBC Lifecycle

`JdbcSqlExecutor` owns the physical statement lifecycle. Standalone and hosted integrations may replace connection participation and transaction ownership, but they must not fork or reimplement statement preparation, binding, execution, result reading, mapping, cleanup, or interceptor completion.

Every acquired resource has one owner. Cleanup failures remain observable, original failures remain primary, and terminal observation failures do not overwrite SQL or cleanup failures.

### Prefer Deep Boundaries

Modules and interfaces should hide meaningful complexity rather than mirror implementation phases. A new helper, layer, wrapper, option, or callback must reduce the number of facts callers need to understand.

Related state, invariants, and failure handling stay with the module that owns them. Do not move complexity upward into generated Mappers or application code merely to keep an internal implementation small.

### Keep the Core Small

The core owns only the reusable runtime contract:

- annotations and public extension APIs;
- immutable execution plans and results;
- JDBC execution;
- minimal standalone local transactions;
- built-in observation interceptors.

The annotation processor, Spring integration, migration tooling, generator, benchmarks, examples, and test infrastructure remain separate modules with one-way dependencies.

### Use Typed Extension Points

Exceptional behavior enters through narrow typed contracts:

- `SqlProvider` for SQL structure that cannot be expressed by the supported static model;
- `ParameterBinder` for application or vendor parameter types;
- `RowMapper` for custom result shapes;
- `ExecutionInterceptor` for observation;
- `ConnectionHandleFactory` for host-managed connection participation.

Extensions must not replace the fixed JDBC lifecycle or become a general runtime plugin chain.

### Keep DataSource Ownership Unambiguous

One generated Mapper is assembled against one `SqlExecutor` and one DataSource domain. Multiple DataSources use disjoint Mapper groups. Dynamic rebinding of the same Mapper to multiple DataSources is outside the contract.

Spring may provide IoC, transaction managers, physical DataSources, and ordered interceptor beans. It does not own Mapper semantics or JDBC execution.

## Compatibility Philosophy

LiteORM supports common SQL Mapper work directly, converts some MyBatis patterns into static LiteORM forms, and rejects features that depend on session state, runtime interpretation, complex object graphs, or hidden framework policy.

Direct support focuses on:

- annotation and XML CRUD;
- controlled dynamic SQL;
- scalar, record, JavaBean, list, optional, cursor, batch, and generated-key contracts;
- explicit transactions and DataSource bindings;
- typed providers, binders, row mappers, and interceptors.

Migration tooling may rewrite deterministic syntax. It must report rather than guess when encountering complex `resultMap` graphs, nested queries, arbitrary OGNL, plugins, caches, or ambiguous Spring configuration.

## Explicit Non-Goals

LiteORM does not add:

- first-level or second-level ORM caches;
- `SqlSession`;
- runtime XML reload or OGNL interpretation;
- lazy loading or complex relationship graphs;
- automatic count queries or framework pagination models;
- distributed transaction management;
- runtime SQL rewriting plugins;
- full MyBatis plugin or API compatibility;
- one Mapper dynamically bound to multiple DataSources.

These omissions are deliberate boundaries, not incomplete features.

## Evidence Before Claims

Architecture and performance claims require executable evidence:

- public contracts are protected by focused tests and API checks;
- generated source is protected by golden and compilation tests;
- JDBC behavior is tested with H2 and production drivers;
- PostgreSQL and MySQL behavior is verified with Testcontainers;
- Spring behavior is verified through physical DataSources and transaction managers;
- Maven and Gradle consumption is verified outside the reactor;
- benchmark claims use equivalent transaction boundaries and reproducible commands.

Optimization follows measurement. A shorter theoretical path is not a performance result.

## Change Decision Checklist

Before accepting a design change, ask:

1. Does it reduce or increase the concepts users must understand?
2. Can the behavior be decided at compile time?
3. Does it preserve the single JDBC lifecycle?
4. Does it keep runtime artifacts independent of processor and tooling code?
5. Is the extension typed and narrow, or is it becoming a general plugin mechanism?
6. Is DataSource and transaction ownership explicit?
7. Is failure and resource ownership observable and deterministic?
8. Is the change justified by a user case, compatibility need, or measured evidence?
9. Can the contract be tested through a stable public boundary?
10. Does the documentation identify one authoritative source of truth?

The preferred change is the smallest one that strengthens these invariants while keeping ordinary Mapper use simple.
