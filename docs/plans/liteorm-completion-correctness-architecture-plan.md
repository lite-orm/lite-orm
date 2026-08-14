# LiteORM Completion, Correctness, and Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Every production change follows TDD. Obsolete implementations and tests may be deleted when their behavior is replaced by clearer executable contracts.

**Goal:** Complete the common Mapper feature set, make transactions, concurrency, exceptions, and resource ownership correct, then review and simplify the architecture before performance optimization and release preparation.

**Architecture:** Preserve compile-time generated Mapper implementations as the default path. Separate connection acquisition, transaction coordination, SQL execution, mapping, and framework assembly behind narrow contracts; local JDBC and Spring must implement the same behavioral contract without runtime Mapper proxies or expression interpretation. Prefer readable composition and explicit ownership over compatibility layers, and use design patterns only where they remove conditionals or clarify lifecycle.

**Tech Stack:** Java 21, Maven, JUnit 5, JDBC, annotation processing, FreeMarker, H2, Spring Boot 3.1.x, Spring JDBC transactions, optional JMH for the final benchmark phase.

---

## Operating Rules

- [x] Implement functionality and correctness before optimization.
- [x] Write a failing executable test before each behavior change.
- [x] Delete print-only, obsolete, duplicated, or misleading tests when replaced by real assertions.
- [x] Delete old production abstractions when a clearer contract fully replaces them; do not preserve compatibility aliases unless explicitly required.
- [x] Keep generated Mapper dispatch static and directly callable.
- [x] Keep XML-over-annotation precedence method-scoped with a compiler warning.
- [x] Keep the supported OGNL-like subset translated directly into native Java.
- [x] Keep public annotations under `org.liteorm.annotation`.
- [x] Make connection and transaction ownership explicit in types and tests.
- [ ] Require all singleton extension implementations to be thread-safe and document the contract.
- [ ] Run focused tests after every TDD cycle and `mvn clean test` before completing a module.
- [ ] Commit completed modules with English commit messages.

## Target Component Flow

```text
Compile time
Mapper annotations/XML
  -> SQL source selection
  -> validated AST / method model
  -> generated MapperImpl

Runtime
Generated MapperImpl
  -> SqlEngine
  -> ExecutionLifecycle
  -> ConnectionProvider
  -> ParameterBinder
  -> JdbcExecutor
  -> RowMapper / generated mapping
  -> resource cleanup

Transaction strategy
  local: LocalTransactionCoordinator owns begin/commit/rollback
  spring: Spring owns boundaries; LiteORM only borrows/releases transaction-bound connections

Assembly
  standalone factory -> local components
  Spring auto-configuration -> Spring connection strategy + ordered extensions
```

## Design Principles and Patterns

- **SRP:** parsing, generation, connection acquisition, transaction boundaries, binding, execution, mapping, and assembly each have one owner.
- **OCP:** new providers, binders, row mappers, interceptors, and transaction strategies do not add central type switches.
- **LSP:** local and Spring strategies honor the same acquisition/release contract; unsupported operations are removed from narrow interfaces instead of throwing by default.
- **ISP:** split the current broad `ConnectionManager` and transaction methods from `SqlEngine`.
- **DIP:** engines receive connection, transaction, execution, and lifecycle abstractions; they do not instantiate concrete managers inside methods.
- **Strategy:** local versus Spring transaction/connection behavior, custom binding, row mapping, and SQL providers.
- **Template Method / explicit lifecycle:** one execution skeleton controls callback order and cleanup.
- **Chain of Responsibility:** retain only for genuinely ordered optional execution stages; fixed JDBC phases become explicit readable calls if the chain obscures ownership.
- **Adapter:** Spring `DataSourceUtils` and transaction state adapt to LiteORM connection semantics.
- **Factory:** standalone and Spring assembly create valid component graphs.
- **Observer:** execution interceptors observe immutable execution events.
- **Builder:** introduce only if `ExecutionPlan` construction remains unreadable after contract cleanup.

---

## Phase P0: Correctness Foundation

### Module C1: Replace Placeholder Transaction Tests

**Files:**
- Delete or rewrite: `lite-orm-core/src/test/java/org/liteorm/test/TransactionProcessorTest.java`
- Add: `lite-orm-core/src/test/java/org/liteorm/test/transaction/LocalTransactionIntegrationTest.java`
- Modify: `lite-orm-examples/basic-mapper/src/test/java/org/liteorm/example/UserMapperE2ETest.java`

- [x] Add an H2 test that begins a local transaction, performs two Mapper writes, commits, and observes both rows.
- [x] Run the test and verify it fails because the current engine creates different transaction-manager instances and does not reliably reuse the transaction connection.
- [x] Add an H2 test that begins a local transaction, writes, rolls back, and observes no row.
- [x] Add an H2 test that two Mapper calls in one local transaction use the same physical connection.
- [x] Add a real nested-begin assertion and remove print-only nested transaction claims.
- [x] Add commit-failure, rollback-failure, auto-commit restoration, and release-failure tests using a deterministic fake connection.
- [x] Run focused transaction tests and the complete core test suite (2026-08-13: 98 core tests passed).

### Module C2: Separate Connection and Transaction Contracts

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/api/ConnectionProvider.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/TransactionCoordinator.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/TransactionOperations.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/LocalTransactionCoordinator.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/DefaultSqlEngine.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/runtime/ConnectionProcessor.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/JdbcConnectionProvider.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/StandaloneSqlEngine.java`
- Remove after migration: `lite-orm-core/src/main/java/org/liteorm/api/ConnectionManager.java`
- Remove after migration: `lite-orm-core/src/main/java/org/liteorm/api/TransactionManager.java`
- Remove after migration: `lite-orm-core/src/main/java/org/liteorm/DefaultTransactionManager.java`

- [x] Define `ConnectionProvider.acquire()` and `release(Connection)` only.
- [x] Define a transaction coordinator that exposes the current transaction connection and owns begin/commit/rollback for local mode.
- [x] Define manual transaction operations separately from `SqlEngine.execute`.
- [x] Inject one stable coordinator instance into standalone assembly.
- [x] Make connection acquisition prefer the coordinator's current transaction connection.
- [x] Ensure execution cleanup releases ordinary connections but never closes an active transaction-owned connection.
- [x] Delete duplicated transaction methods from the old connection abstraction.
- [x] Delete old transaction implementations after all call sites migrate.
- [x] Run local transaction tests and `mvn -pl lite-orm-core test`.

### Module C3: Spring Transaction Delegation

**Files:**
- Create: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/SpringConnectionProvider.java`
- Remove: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/SpringTransactionProcessor.java`
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/LiteOrmAutoConfiguration.java`
- Modify: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot/LiteOrmAutoConfigurationIntegrationTest.java`

- [x] Adapt `DataSourceUtils.getConnection/releaseConnection` to the narrow connection-provider contract.
- [x] Remove begin/commit/rollback methods that only throw `UnsupportedOperationException`.
- [x] Verify one Spring transaction shares one physical connection across multiple generated Mapper calls.
- [x] Verify normal completion commits.
- [x] Verify runtime interceptor failure rolls back.
- [x] Verify rollback-only rolls back.
- [x] Verify an interceptor failure cannot bypass Spring rollback.
- [x] Verify non-transaction calls release connections normally with an explicit acquisition/release assertion.
- [x] Run Spring focused tests and the full reactor (2026-08-13: 5 Spring integration tests and full reactor passed).

### Module C4: Exception and Resource Lifecycle

**Files:**
- Modify: `lite-orm-core/src/main/java/org/liteorm/DefaultSqlEngine.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/SqlResult.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/SqlExecutionException.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/FreemarkerCodeGenerator.java`
- Add: `lite-orm-core/src/test/java/org/liteorm/test/runtime/ResourceLifecycleTest.java`

- [x] Define one exception contract carrying statement ID, source type, cause, and safe SQL context.
- [x] Stop wrapping generated Mapper failures in generic `RuntimeException`.
- [x] Make `SqlEngine.execute` return only successful results and throw `SqlExecutionException` on failure.
- [x] Verify result set, statement, and ordinary connection close in reverse ownership order.
- [x] Verify cleanup exceptions are suppressed onto the primary failure.
- [x] Verify interceptor callback failures do not replace JDBC or transaction failures.
- [x] Verify active local and Spring transaction connections remain open after one Mapper call through transaction reuse tests.
- [x] Run resource and exception tests, then core tests.

## Phase P1: Complete Common Mapper Functionality

### Module F1: Real JDBC Batch

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/api/BatchExecutionPlan.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/BatchSqlTask.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/SqlExecutor.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/SimpleSqlExecutor.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/runtime/ExecutionProcessor.java`
- Modify: compiler model and generator files under `lite-orm-core/src/main/java/org/liteorm/compile`
- Add H2 fixtures under `lite-orm-examples/basic-mapper`

- [x] Define `@Batch` and XML `<batch>` methods as one `List<T>` input returning JDBC `int[]` update counts.
- [x] Generate ordered parameter sets for annotation and XML batch methods as static Java loops.
- [x] Use `PreparedStatement.addBatch/executeBatch`.
- [x] Execute the whole batch on one connection and join the active local or Spring transaction boundary.
- [x] Preserve JDBC `BatchUpdateException` as the cause so partial update counts remain available.
- [x] Delete the disconnected `SqlExecutor` and `SimpleSqlExecutor` implementations.
- [x] Add concurrent, local rollback, annotation, XML, empty-batch, and compile-time contract tests.

### Module F2: Generated Keys

- [x] Add an explicit generated-key annotation/API under `org.liteorm.annotation`.
- [x] Support only an explicit Mapper return value of `long` or `Long` in the first version; do not mutate input beans or replace records implicitly.
- [x] Restrict generated keys to opted-in static `INSERT` methods and reject batch, provider, dynamic SQL, and unsupported return-type combinations at compile time.
- [x] Generate `RETURN_GENERATED_KEYS` statement creation only for opted-in methods.
- [x] Read exactly one generated key and close its `ResultSet` before the statement and ordinary connection.
- [x] Add H2 integration tests and compile-time failure tests.

### Module F3: Type and Null Semantics

- [x] Keep `ExecutionContext` as a temporary per-call internal state carrier during P1/P2; do not add new string-keyed core state or expose it as a user extension API.
- [x] Add real tests for primitives, wrappers, `BigDecimal`, `LocalDate`, `LocalDateTime`, `Instant`, enums, byte arrays, SQL nulls, and nullable custom binders.
- [x] Define single-result behavior: reference returns yield `null` for zero rows, primitive single-result returns are rejected when absence cannot be represented, and more than one row throws a dedicated non-unique-result exception.
- [x] Define update return types: `void`, `int`, `long`, and reject unsupported return types.
- [x] Replace remaining generated unchecked casts where practical.
- [x] Delete obsolete type-inference paths that emit TODO/null code.

### Module F4: Extension Combination Completion

- [x] Decide and implement custom binders in dynamic SQL using generated binder slots per emitted parameter.
- [x] Decide and implement provider-bound custom parameters through a typed `BoundParameter<T>` binder extension.
- [x] Verify row mappers for single and list results with providers and XML.
- [x] Reject ambiguous combinations at compile time.
- [x] Keep every extension invocation direct and reflection-free.

**Execution TODO (follow in order):**

- [x] Design dynamic SQL binder slots as a generated list aligned one-to-one with emitted JDBC parameters.
- [x] Implement dynamic SQL binder compilation and generated binder propagation; verified by `CustomAdapterCompilationTest#generatesBinderSlotsForDynamicSqlParameters` and the H2 metadata Mapper scenario.
- [x] Define and implement the Provider parameter-binding contract with typed `BoundParameter` binder metadata.
- [x] Verify custom `RowMapper` combinations for Provider and XML single/list results.
- [x] Reject ambiguous extension combinations, including whole-parameter binders applied to property expressions and collection binders implicitly applied to `foreach` items.
- [x] Verify generated extension paths contain no reflection or runtime adapter lookup.
- [x] Run focused F4 tests, `mvn clean test`, and `git diff --check`; then update the F4 checkboxes and commit in English.
- [x] Continue with F5 SQL/XML compatibility closure.
- [ ] Continue with P2 concurrency and thread-safety verification.
- [ ] Continue with P3 assembly, transaction integration, and architecture review.
- [ ] Continue with P4 benchmarks, optimization, and release readiness only after correctness and architecture work are complete.

### Module F5: SQL and XML Compatibility Closure

- [x] Build a behavior matrix for all supported dynamic tags and edge cases.
- [x] Add real tests for nested `choose/trim/foreach`, empty values, bind scope, include scope, and whitespace normalization.
- [x] Keep complex `resultMap` unsupported until a separate design is approved.
- [x] Ensure unsupported XML attributes and elements fail rather than being ignored.
- [x] Delete legacy parser tests that only print expected behavior.

**Remaining F5 closure tasks:**

- [x] Reject invalid `<choose>` structures: unsupported children, more than one `<otherwise>`, and `<when>` after `<otherwise>`.
- [x] Detect missing and cyclic `<include>` references with deterministic compiler diagnostics.
- [ ] Define and test an all-empty `<set>` update as a pre-JDBC failure rather than emitting invalid SQL.
- [ ] Run the full reactor, generated-source reflection scan, and `git diff --check`; then commit F5 in English.

| XML feature | Intended compile-time behavior | Current verification / gap |
| --- | --- | --- |
| `<if test>` | Translate the supported expression subset to native Java and emit its body conditionally. | Nested cases exist; empty-body and missing-attribute failures still need real tests. |
| `<choose>/<when>/<otherwise>` | Emit first-match native Java branching with at most one fallback. | Nested coverage exists; invalid child order/cardinality still needs rejection tests. |
| `<foreach>` | Emit a typed Java loop with ordered JDBC parameters and separators. | List/array coverage exists; empty collections, nested loops, scope leakage, and unsupported attributes need real tests. |
| `<where>` | Emit `WHERE` only for non-empty content and remove leading `AND`/`OR`. | Basic coverage exists; whitespace and nested-empty behavior need real assertions. |
| `<set>` | Emit `SET` only for non-empty content and remove trailing commas. | Basic coverage exists; all-empty update behavior must be defined and rejected or tested. |
| `<trim>` | Apply prefix/suffix and override tokens at runtime to generated fragments. | Basic nested coverage exists; case/whitespace normalization needs real assertions. |
| `<bind>` | Translate the expression to a scoped native Java local variable. | Mixed, bind-only, and included-fragment generation are covered. |
| `<include>` | Resolve SQL fragments during compilation; no runtime XML lookup. | Text and multi-node dynamic fragments are covered; missing/cyclic references still need deterministic diagnostics. |
| `#{...}` | Emit `?`, value slot, and aligned Binder slot. | Static and dynamic paths covered; nested dynamic ordering remains part of F5 tests. |
| `${...}` | Allow only compile-time-approved safe substitution roots. | Safety validation exists; nested fragment coverage remains to verify. |
| `resultMap` | Fail compilation until a separate complex mapping design is approved. | Existing failure coverage; keep unsupported. |
| Unknown XML elements | Fail compilation rather than ignore them. | Existing unsupported-element fixture; retain and strengthen if needed. |
| Unknown/missing XML attributes | Fail compilation rather than silently use empty/default values. | Required and unknown attribute diagnostics are covered. |
| SQL whitespace | Produce stable executable SQL without token concatenation or accidental blank clauses. | Nested H2 coverage verifies `trim`, included fragments, and parameter placeholders use stable separators. |

## Phase P2: Concurrency and Thread Safety

### Module R1: Runtime Concurrency

- [ ] Add multithreaded H2 tests reusing one generated Mapper and one engine.
- [ ] Verify execution contexts, statements, results, and transaction state never cross threads.
- [ ] Verify local transactions are isolated and ThreadLocal state is always removed.
- [ ] Verify Spring transaction-bound connections remain isolated by thread.
- [ ] Document that Provider, Binder, RowMapper, and Interceptor singleton instances must be thread-safe and stateless or externally synchronized.
- [ ] Add a compile-time or construction-time factory option only if real use cases require stateful adapters.

### Module R2: Compiler Concurrency and Cache Isolation

- [ ] Replace static XML DOM caches if they can leak across compiler invocations or classpaths.
- [ ] Add parallel compilation tests for different Mapper XML files with identical resource names in isolated directories.
- [ ] Verify include resolution has no cross-thread state leak.
- [ ] Remove ThreadLocal parser state if explicit parse context is clearer.

## Phase P3: Assembly and Architecture Review

### Module A1: Component Assembly

- [ ] Introduce a readable standalone assembly facade/factory.
- [ ] Keep generated Mapper constructors minimal and stable.
- [ ] Make Spring auto-configuration assemble the same core component graph with Spring strategies.
- [ ] Provide user override points for connection provider, transaction coordinator, engine, and interceptors.
- [ ] Add startup diagnostics for invalid or duplicate Mapper registration.

### Module A2: End-to-End Architecture Review

- [ ] Document upstream inputs, compile-time transformations, generated artifacts, runtime components, downstream JDBC resources, and user-facing APIs.
- [ ] Review package boundaries and dependency direction.
- [ ] Review every public interface for SRP, OCP, LSP, ISP, and DIP.
- [ ] Review applicable GoF 23 patterns and remove accidental or ceremonial patterns.
- [ ] Inventory every `SqlProcessor`, its required predecessor state, produced state, ordering constraint, optionality, failure semantics, and real user extension requirement.
- [ ] Evaluate whether Chain of Responsibility is correct for an ORM execution pipeline by separating fixed JDBC phases from optional cross-cutting behavior; compare it explicitly with Template Method, Strategy, Interceptor, and Decorator.
- [ ] Treat connection acquisition, statement preparation, parameter binding, execution, result extraction, and cleanup as fixed physical phases unless evidence shows that arbitrary chain reordering is a valid user requirement.
- [ ] Decide whether the current processor chain improves readability and type safety; replace fixed physical phases with one explicit execution lifecycle if it does not.
- [ ] Move logging, audit, slow-query monitoring, metrics, tracing, and similar before/after concerns to typed `ExecutionInterceptor` callbacks instead of string-keyed `ExecutionContext` attributes.
- [ ] Decide the final `ExecutionContext` fate: remove it entirely or narrow it to a package-private JDBC resource scope with explicit connection ownership and no generic `Map<String, Object>`.
- [ ] If a resource scope remains, model mutually exclusive query, update, batch, and generated-key outcomes as typed immutable results rather than nullable fields on one mutable context.
- [ ] Add characterization tests before lifecycle refactoring and verify resource-close order, suppressed cleanup failures, local/Spring transaction connection ownership, interceptor ordering, generated keys, batch execution, and concurrent Mapper reuse after the refactor.
- [ ] Review generated source readability, naming, exception messages, and debugging experience.
- [ ] Record accepted trade-offs and rejected alternatives.

### Module A3: Remove Legacy Code and Tests

- [ ] Delete superseded executors, managers, contexts, enums, processors, and compatibility constructors.
- [ ] Delete tests that only print claims, duplicate stronger tests, or validate removed designs.
- [ ] Run `rg` checks for removed type names and stale documentation.
- [ ] Run `mvn clean test` after each deletion group.

## Phase P4: Optimization and Release

### Module O1: Benchmark and Performance Work

- [ ] Migrate old plan M10 benchmark scenarios here.
- [ ] Define reproducible comparisons with hand-written JDBC and MyBatis.
- [ ] Measure startup separately from steady-state execution.
- [ ] Measure select, dynamic select, update, batch, mapping, provider, custom mapper, and interceptor overhead.
- [ ] Optimize only after profiling identifies a bottleneck.
- [ ] Confirm documentation claims do not exceed evidence.

### Module O2: Release Readiness

- [ ] Migrate the old release checklist here.
- [ ] Document Java version, Maven coordinates, processor setup, supported subset, transaction modes, concurrency contract, extensions, and known gaps.
- [ ] Add CI commands for full tests and optional benchmarks.
- [ ] Run final architecture review and `mvn clean test`.

### Module O3: Compiler Dependency Simplification (Low Priority)

- [ ] Replace `templates/mapper-impl.ftl` with deterministic Java source assembly after F5, P2, and P3 are complete.
- [ ] Move the fixed Mapper class shell and dynamic SQL helper methods into a focused Java source writer without changing generated public constructors or Mapper behavior.
- [ ] Rename `FreemarkerCodeGenerator` to a technology-neutral name such as `JavaSourceCodeGenerator`.
- [ ] Remove `${.now}` and add a reproducible-generation test proving identical inputs produce byte-for-byte identical Java source.
- [ ] Delete the FreeMarker template, remove the Core FreeMarker dependency and parent dependency-management entry, and verify packaged resources no longer contain `/templates`.
- [ ] Run generated-source compilation tests, `mvn clean test`, and dependency analysis before committing the simplification.

## Deferred Backlog

The old backlog is carried forward but remains deferred until P0-P3 are complete:

- SQL metadata export for IDE/AI/static analysis.
- Production observability pack.
- Type-safe query DSL.
- First-level and second-level cache.
- Read/write splitting, sharding, and multi-tenant routing.
- Complex multi-row `resultMap` graph aggregation.
