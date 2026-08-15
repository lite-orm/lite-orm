# LiteORM Architecture Review

Date: 2026-08-14

> Historical review notice: this document records the architecture reviewed on August 14, 2026. Its `SqlEngine`, `ConnectionProvider`, `TransactionCoordinator`, processor-chain, and `ExecutionContext` conclusions are superseded by the active [LiteORM Runtime Architecture Implementation Plan](../plans/liteorm-runtime-architecture-implementation-plan.md).

## Architectural Goal

LiteORM is a compile-time Mapper platform. SQL structure, supported dynamic expressions, parameter order, binder selection, and result mapping should be resolved into ordinary Java source during annotation processing. Runtime code should perform only the physical JDBC lifecycle and explicitly approved extension calls.

The primary design constraint is therefore not MyBatis compatibility. It is a readable, deterministic path from Mapper source to generated code to JDBC, without runtime Mapper proxies, runtime XML interpretation, expression engines, or reflection-based binding and mapping.

## End-to-End Component Flow

```text
User Mapper interface
  + LiteORM annotations
  + optional Mapper XML
        |
        v
LiteOrmProcessor
        |
        v
CompilePipeline
  -> XML/annotation precedence resolution
  -> XML validation and include expansion
  -> dynamic SQL AST
  -> parameter/binder/result mapping model
        |
        v
CodeGenerator
        |
        v
Generated *MapperImpl
  -> native Java dynamic SQL
  -> ordered ExecutionPlan
  -> direct Provider/Binder/RowMapper calls
        |
        v
SqlEngine
  -> interceptor before callbacks
  -> acquire or join Connection
  -> prepare statement
  -> bind parameters
  -> execute
  -> extract typed-neutral JDBC result
  -> interceptor success/failure callbacks
  -> close ResultSet and PreparedStatement
  -> release non-transactional Connection
        |
        v
Generated Mapper result mapping and user return value
```

## Dependency Direction

| Area | Responsibility | Allowed dependencies | Review result |
| --- | --- | --- | --- |
| `org.liteorm.annotation` | User declarations consumed by the compiler | Java annotation types only | Correct and stable. |
| `org.liteorm.compile` | Parse, validate, model, and generate Java | Annotation-processing APIs, DOM parser, code writer | Correct direction; it may depend on runtime API contracts emitted into generated code. Runtime must not depend on compiler types. |
| `org.liteorm.api` | Small contracts shared by generated code, runtime, and integrations | JDK/JDBC only | Correct direction, but compatibility aliases should be removed after migration. |
| `org.liteorm` runtime facade | Assembly, engine, local transactions, resource scope | `org.liteorm.api`, runtime implementation | Correct role; mutable lifecycle details should become package-private. |
| `org.liteorm.runtime` | JDBC implementation details | `org.liteorm.api`, package-private runtime scope | Fixed JDBC phases should not remain public reorderable processors. |
| Spring starter | Supply Spring connection/transaction strategies and register generated Mappers | Core public API and Spring | Correct direction after adopting `LiteOrm.engine(...)`; core has no Spring dependency. |

## Public Contract Review

| Contract group | SRP / ISP / DIP assessment | Decision |
| --- | --- | --- |
| `SqlEngine` | One generated-code execution entry point; depends on `ExecutionPlan`, not JDBC implementation | Keep. |
| `ExecutionPlan`, `BatchExecutionPlan` | Immutable execution input abstraction | Keep; eventually remove legacy `SqlTask` and `BatchSqlTask` aliases. |
| `SqlResult` | Immutable runtime output carrier | Keep; ensure query/update/batch/generated-key outcomes are not represented by ambiguous nullable mutable state internally. |
| `ConnectionProvider` | Narrow host-specific acquire/release strategy | Keep. Implementations must be thread-safe. |
| `TransactionCoordinator` | Narrow strategy for the connection bound to the current transaction | Keep. Local and Spring ownership remain external strategies. |
| `TransactionOperations`, `TransactionContext` | Standalone transaction boundary API | Keep for standalone use; do not expose it through Spring-managed execution. |
| `SqlProvider`, `ParameterBinder`, `RowMapper` | Typed exceptional extension strategies | Keep. Generated code invokes concrete instances directly. |
| `ExecutionInterceptor`, `ExecutionInvocation` | Before/after cross-cutting observation | Keep and use for logging, metrics, tracing, audit, and slow-query reporting. Completion mutation should remain engine-owned. |
| Exception hierarchy | User-facing failure categories | Keep, but runtime lifecycle validation should use LiteORM-specific exceptions instead of generic `RuntimeException` where practical. |
| `SqlProcessor` and concrete processor classes | Exposes fixed lifecycle phases as an arbitrarily reorderable public chain | Remove from the public extension model after characterization tests. |
| `ExecutionContext` | Public mutable bag containing resources, mutually exclusive outcomes, and string-keyed attributes | Replace with a package-private typed JDBC scope, then remove generic attributes. |
| `LiteOrmConfig` | Global mutable singleton not used by current assembly | Delete as legacy code. |

## SOLID Review

### Single Responsibility

- Compiler parsers, model building, generation, connection acquisition, transaction coordination, and mapping are appropriately separated.
- `ExecutionContext` violates SRP by combining resource ownership, transaction flags, statement state, result variants, and extension storage.
- `SqlProcessor` implementations appear separated by phase, but the engine transfers their hidden preconditions through one mutable object. This moves complexity rather than removing it.

### Open/Closed

- Providers, binders, row mappers, interceptors, connection providers, and transaction coordinators are useful Strategy extension points.
- Arbitrary insertion or reordering inside the physical JDBC lifecycle is not a valid general extension requirement. Keeping fixed phases open for replacement makes invalid assemblies easy and correct assemblies harder to read.

### Liskov Substitution

- Typed extension interfaces have substitutable contracts when implementations are stateless/thread-safe and honor null/result rules.
- `SqlProcessor` implementations are not freely substitutable because each requires undocumented predecessor state. This is evidence that they are phases, not interchangeable strategies.

### Interface Segregation

- Current typed APIs are narrow.
- `ExecutionContext` exposes far more state than each phase needs, and `SqlProcessor` grants every processor read/write access to all lifecycle state. The replacement must pass explicit typed inputs or use one package-private scope controlled by one lifecycle owner.

### Dependency Inversion

- Core assembly depends on `ConnectionProvider`, `TransactionCoordinator`, and `ExecutionInterceptor` abstractions. Spring supplies implementations without leaking Spring into core.
- The engine should own the fixed lifecycle algorithm and depend on narrow strategies. It should not depend on a user-configurable list of its own physical implementation steps.

## GoF Pattern Review

| Pattern | Fit | Decision |
| --- | --- | --- |
| Strategy | Strong fit for connection, transaction, provider, binder, row mapper, and optional policies | Keep. |
| Builder | Strong fit for readable engine assembly with optional strategies | Keep through `LiteOrm.EngineBuilder`. |
| Facade | Strong fit for standalone and host assembly entry points | Keep through `LiteOrm`. |
| Interceptor | Strong fit for ordered before/after observational concerns | Keep as the cross-cutting extension mechanism. |
| Template Method | Conceptual fit for a fixed JDBC lifecycle, but inheritance is unnecessary | Implement the fixed algorithm through composition in one lifecycle class rather than a subclass API. |
| Chain of Responsibility | Weak fit for mandatory ordered JDBC phases; callers cannot reasonably choose arbitrary handlers or order | Remove for connection/prepare/bind/execute/extract/cleanup. |
| Decorator | Possible for wrapping `SqlEngine`, but duplicates interceptor semantics | Do not add unless an integration needs replacement of the whole execution boundary. |
| Factory Method / Abstract Factory | No current family of stateful adapters justifies it | Do not add an adapter factory until a real use case exists. |
| Singleton | Incorrect for mutable global configuration | Delete `LiteOrmConfig`; host containers or explicit assembly own singleton lifetime. |

The remaining GoF patterns do not solve a current LiteORM problem and should not be introduced ceremonially.

## Processor Inventory and Ordering Problem

| Current processor | Hidden required state | Produced state | Reorderable? | Final direction |
| --- | --- | --- | --- | --- |
| `ConnectionProcessor` | `ConnectionProvider`, optional transaction connection | `Connection` and ownership flag | No; must be first | Inline into lifecycle acquisition. |
| `ParameterProcessor` | Connection, SQL, binder metadata | PreparedStatement with bound parameters | No; must follow acquisition | Inline into prepare/bind phase. |
| `ExecutionProcessor` | PreparedStatement and possibly batch parameter sets | ResultSet, update count, batch counts, generated key | No; must follow binding | Inline into execute phase. |
| `ResultProcessor` | ResultSet | Materialized rows | No; only valid for query results | Inline into extraction phase. |
| `LoggingProcessor` | Needs both before and after moments | Logs and timing attribute | Cannot work correctly as a one-shot phase | Replace with interceptor. |
| `SlowQueryMonitorProcessor` | Needs start and completed duration | Warning/metric | Cannot work correctly as a one-shot phase | Replace with interceptor. |
| `SqlAuditProcessor` | Needs completed outcome and duration | Audit event | Currently runs before execution in Spring order | Replace with interceptor. |

The Spring configuration currently places logging, slow-query, and audit processors before parameter preparation. A processor is invoked only once, so these classes do not have a reliable post-execution callback. `updateCount` also defaults to zero, making “completed” checks ambiguous. This is a correctness and readability problem, not merely an optimization opportunity.

## Decisions

1. Replace the processor chain with one explicit, package-private JDBC execution lifecycle owned by `DefaultSqlEngine`.
2. Keep connection acquisition, statement preparation, binding, execution, extraction, and cleanup in a fixed order.
3. Keep `ExecutionInterceptor` as the only ordinary before/after cross-cutting runtime extension point.
4. Convert logging, slow-query monitoring, and audit behavior to interceptors before deleting their processor forms.
5. Replace public `ExecutionContext` with a package-private `JdbcExecutionScope` only if a small mutable resource holder materially improves cleanup clarity. It must not contain a generic attribute map.
6. Represent the final outcome with immutable `SqlResult`; do not retain nullable query/update/batch/generated-key result variants in public mutable state.
7. Preserve `SqlEngine`, generated Mapper constructors, transaction behavior, resource close order, failure suppression, generated keys, batch counts, and concurrent Mapper reuse during refactoring.
8. Delete legacy compatibility types and unused dependencies only after the replacement lifecycle has full characterization coverage.

## Rejected Alternatives

- **Keep the chain and add ordering metadata:** rejected because metadata documents invalid flexibility rather than removing it.
- **Give every processor before/after methods:** rejected because that recreates interceptors while still exposing fixed phases as plugins.
- **Expose `ExecutionContext` as the universal extension API:** rejected because string keys and mutable JDBC resources weaken type safety and ownership.
- **Use inheritance-based Template Method:** rejected because the lifecycle should not become a subclass customization surface.
- **Create per-call Provider/Binder/RowMapper factories now:** rejected because no demonstrated stateful use case offsets the additional API and allocation complexity.

## Implementation Sequence

1. Add characterization tests for lifecycle order, cleanup suppression, transactions, generated keys, batch results, interceptor order, and concurrency.
2. Introduce the fixed JDBC lifecycle behind the existing `SqlEngine` API.
3. Convert logging, slow-query monitoring, and audit to typed interceptors.
4. Remove `SqlProcessor`, concrete fixed processors, generic context attributes, and Spring processor assembly.
5. Delete `LiteOrmConfig`, compatibility constructors/types proven unused, and unused dependencies.
6. Re-run generated-source, full reactor, concurrency, and dependency checks after each deletion group.
