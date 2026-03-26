## Context

LiteORM already contains the major building blocks of a compile-time ORM: `LiteOrmProcessor` discovers MyBatis `@Mapper` interfaces, the compile package parses SQL from annotations and XML into AST structures, and the runtime package executes SQL through a processor chain. What is missing is a stable product-level design that turns these pieces into a coherent MyBatis replacement with explicit behavior for generated mapper code, dynamic SQL compilation, transaction participation, and migration boundaries.

This change crosses both existing modules:
- `lite-orm-core`: annotation processing, XML and annotation parsing, code generation templates, SQL execution runtime, transaction contracts, and tests.
- `lite-orm-spring-boot-starter`: auto-configuration and transaction integration with Spring-managed infrastructure.

Constraints:
- The hot path must avoid runtime reflection for mapper dispatch, parameter binding, and result mapping.
- MyBatis compatibility should focus on high-value mapper patterns first rather than promise full parity.
- Generated code must remain debuggable and deterministic so compiler failures are actionable.

## Goals / Non-Goals

**Goals:**
- Generate static mapper implementations, SQL renderers, parameter binders, and result mappers at compile time.
- Support common MyBatis XML and annotation workflows, including dynamic SQL, without runtime OGNL or reflective method dispatch.
- Define a transaction-aware runtime contract that works with both LiteORM-managed and Spring-managed transactions.
- Produce clear compile-time diagnostics for unsupported mapper constructs.
- Keep the runtime small and extension-oriented so caching, auditing, and sharding can layer on later.

**Non-Goals:**
- Full MyBatis feature parity in one change, especially plugins, lazy loading graphs, or every legacy XML feature.
- Distributed transactions, second-level cache, pagination DSL, or sharding implementation.
- Runtime interpretation of XML or dynamic expressions after compilation.
- Hiding unsupported behavior behind partial runtime fallbacks.

## Decisions

### 1. Use the annotation processor as the single compilation entry point

The processor will remain the only entry point for turning mapper contracts into executable code. Mapper interfaces, XML resources, and method annotations feed a normalized compilation model that is then used to generate Java sources.

Rationale:
- Fits the current `LiteOrmProcessor` and `CompilePipeline` structure.
- Keeps all validation in one place and allows fail-fast compiler diagnostics.
- Ensures the produced runtime path is static and JIT-friendly.

Alternatives considered:
- Runtime bytecode generation: rejected because it weakens startup determinism and complicates debugging.
- Split compiler and runtime schema files manually authored by users: rejected because it makes mapper authoring heavier than MyBatis.

### 2. Compile dynamic SQL into dedicated renderer classes, not interpreted strings

Dynamic SQL will be parsed into an AST during compilation and emitted as Java renderer methods or helper classes per mapper method. A renderer returns both final SQL text and an ordered parameter list.

Rationale:
- Preserves MyBatis-style `if`, `choose`, `trim`, `where`, `set`, `foreach`, `sql`, and `include` behavior.
- Eliminates reflective expression evaluation and string-template logic on the hot path.
- Makes nested dynamic SQL testable as plain Java logic.

Alternatives considered:
- Keep XML AST around and interpret it at runtime: rejected because it reintroduces runtime parsing and branching overhead.
- Generate raw string concatenation directly in mapper methods: rejected because renderer helpers are easier to validate, reuse, and test.

### 3. Separate generated execution plans from the runtime execution pipeline

Generated mapper code will produce a small execution plan per method call: SQL text, bound parameters, statement type, expected result contract, and transaction hints. The runtime engine executes that plan through the existing processor chain interfaces.

Rationale:
- Reuses the existing `DefaultSqlEngine`, `SqlTask`, and processor model instead of replacing it.
- Lets generated code stay thin while runtime concerns like connection acquisition, retries, logging, and auditing remain extensible.
- Creates a stable seam for both local and Spring-managed transactions.

Alternatives considered:
- Inline all JDBC calls into generated mappers: rejected because it duplicates runtime concerns and removes extension hooks.
- Keep runtime in charge of deriving result and parameter metadata: rejected because that would require reflective metadata lookup.

### 4. Support dual transaction modes with one transaction context contract

LiteORM will define one transaction scope contract used by generated mappers and the SQL engine. The default implementation manages local JDBC transactions. The Spring Boot starter adapts that contract to an active Spring transaction when present instead of opening an independent local transaction.

Rationale:
- Matches the current module split and user expectation for Spring integration.
- Prevents double-transaction bugs when running inside `@Transactional`.
- Makes nested transaction policy explicit per integration mode.

Alternatives considered:
- Only support self-managed transactions first: rejected because it limits real adoption.
- Depend directly on Spring in core: rejected because it couples the runtime to one environment.

### 5. Define a compatibility subset and fail compilation outside it

The change will document and enforce a supported MyBatis subset rather than attempt silent degradation. Unsupported constructs must surface as compile errors with a message that points to the mapper method or XML node.

Rationale:
- Keeps migration predictable.
- Prevents subtle runtime behavior differences.
- Lets the project add compatibility incrementally with confidence.

Alternatives considered:
- Best-effort runtime fallback to MyBatis: rejected because it violates the no-runtime-reflection goal and complicates packaging.

## Risks / Trade-offs

- [Dynamic SQL parity gaps] -> Start with the most used tags, encode behavior in conformance tests copied from realistic MyBatis fixtures, and fail fast on unsupported constructs.
- [Generated code size growth] -> Emit small helper classes per mapper or method, share reusable renderer fragments, and avoid duplicating result mapping logic across methods.
- [Transaction semantics differ from MyBatis or Spring edge cases] -> Define explicit propagation and nested scope rules, then cover both local and Spring-managed scenarios with integration tests.
- [Compile-time diagnostics become noisy] -> Normalize compiler errors into mapper method and XML location references instead of surfacing raw parser exceptions.
- [Migration expectations are too broad] -> Publish the supported subset and examples before positioning LiteORM as a drop-in replacement.

## Migration Plan

1. Finalize the supported mapper contract and dynamic SQL subset in specs.
2. Refactor the compile pipeline to emit normalized metadata and generated renderer helpers without breaking existing tests.
3. Align the runtime SQL engine with generated execution plans and explicit transaction scope contracts.
4. Add Spring Boot starter integration for managed transaction participation.
5. Add end-to-end compatibility fixtures that mirror representative MyBatis annotation and XML mappers.
6. Document migration steps, unsupported features, and examples for mixed MyBatis and LiteORM adoption.

Rollback strategy:
- Generated code remains additive to the codebase and can be disabled by removing the processor or feature flagging the starter auto-configuration.
- Existing runtime abstractions stay in place so the refactor can be reverted module by module if needed.

## Open Questions

- Which expression language subset is allowed inside dynamic SQL tests and conditions, and should it be limited to Java-accessible properties plus simple operators?
- Should nested local transactions be rejected, translated to savepoints, or deferred to a later change?
- How much of MyBatis result mapping beyond direct property mapping is required for the first production-ready milestone?
