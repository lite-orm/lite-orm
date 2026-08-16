# LiteORM Core GA Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn LiteORM core from a sound compile-time Mapper/JDBC kernel into a production-grade GA core with precise transaction semantics, bounded query execution, robust mapping contracts, deterministic outcomes, hardened APIs, and real-database verification.

**Architecture:** Keep generated Mappers dependent only on `SqlExecutor`, keep the physical JDBC lifecycle fixed, and keep DataSource routing/pooling/distributed transactions outside core. Split executor-facing connection participation from application transaction completion, add immutable per-statement controls, make mapping contracts explicit, and validate behavior against real production JDBC drivers before optimization.

**Tech Stack:** Java 21, JDBC, JUnit 5, Maven, H2 for focused tests, Testcontainers for PostgreSQL/MySQL compatibility tests, JMH only after correctness gates pass.

---

## Scope and Decisions

- No historical release exists, so public APIs may be replaced directly. Do not retain deprecated bridges, compatibility aliases, or duplicate abstractions.
- Core GA covers compile-time Mapper generation, JDBC execution, local transaction semantics, statement controls, result mapping, error taxonomy, concurrency, and database compatibility.
- Core GA does not own connection pools, migrations, distributed transactions, retries, second-level caches, tenant/shard/read-write routing, or Spring feature design.
- Spring source may receive mechanical API migration only when required to keep the reactor compiling; Spring behavior expansion is outside this plan.
- One Mapper interface is assembled once against one DataSource domain. Multiple DataSources use disjoint Mapper groups.
- The old plan's speculative method-level DataSource binding and core routing SPI items are discarded.
- The old plan's API hardening, immutable `SqlResult`, exception cleanup, obsolete asset cleanup, narrative-test cleanup, and benchmark gate are retained here.
- The old Spring/core shared lifecycle characterization item is deferred to a Spring-specific plan.

## Responsibility Boundary

### Core built-in capabilities

- Compile-time Mapper contracts and generated implementations.
- Fixed JDBC statement lifecycle, binding, execution, result extraction, generated keys, and batch behavior.
- Statement-level timeout, fetch size, maximum-row safety caps, cancellation hooks, and scope-bound large-result consumption.
- SQL pagination through ordinary dynamic Mapper parameters rendered and bound into dialect-appropriate SQL such as `LIMIT`/`OFFSET`; core does not treat JDBC `maxRows` as pagination.
- Common scalar, Record, JavaBean, optional, list, and update-count result contracts.
- Minimal standalone local transactions: one DataSource, one connection, commit on success, rollback on failure, nested callbacks join the root, and rollback-only prevents accidental commit. This intentionally follows the same narrow role as a MyBatis-style internal JDBC transaction implementation rather than duplicating a host transaction manager.
- Failure certainty, exception taxonomy, redaction, resource ownership, thread safety, and production-driver compatibility.

### Core extension SPIs

- `SqlProvider` for exceptional SQL structure.
- `ParameterBinder` for application or vendor parameter types.
- `RowMapper` for application or vendor result shapes.
- `ExecutionInterceptor` for observation only; it does not replace JDBC phases.
- `ConnectionHandleFactory` for host-managed connection participation such as Spring.

### Outside core

- Transaction propagation modes, savepoints, declarative isolation/read-only policies, rollback rules, and production transaction orchestration belong to Spring or another host transaction manager.
- Connection pooling, retries, migrations, distributed transactions, caches, and tenant/shard/read-write routing remain application infrastructure or optional integrations.
- Pagination DSLs, automatic dialect rewriting, page-count queries, and page-result policies remain optional compiler/integration features. Core only needs to preserve dynamic SQL parameters so the final SQL performs the actual pagination.

## Delivery Order

1. Correct transaction and connection contracts.
2. Define execution certainty and observer isolation.
3. Harden immutable result ownership and generated mapping contracts.
4. Add bounded and streaming query execution after mapping contracts stabilize.
5. Normalize exceptions, redaction, and immutable ownership.
6. Verify PostgreSQL/MySQL and failure behavior.
7. Benchmark only after all correctness gates pass.

---

## Phase G1: Transaction and Connection Contracts

### Task 1: Split Connection Participation From Transaction Completion

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/api/ConnectionHandle.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/ConnectionHandleFactory.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/api/Transaction.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/api/TransactionFactory.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/TransactionCallback.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/TransactionalExecutor.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/jdbc/JdbcSqlExecutor.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/JdbcAssembly.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/transaction/SimpleTransaction.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/transaction/SimpleTransactionFactory.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/transaction/SimpleConnectionHandleFactory.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/transaction/SimpleTransactionalExecutor.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/transaction/TransactionContractTest.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/transaction/SimpleTransactionTest.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/transaction/SimpleTransactionConcurrencyTest.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/transaction/TransactionDomainContractTest.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/jdbc/JdbcSqlExecutorTest.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/LiteOrmAssemblyTest.java`
- Delete: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/SpringTransaction.java`
- Delete: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/SpringTransactionFactory.java`
- Create: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/SpringConnectionHandle.java`
- Create: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/SpringConnectionHandleFactory.java`
- Mechanical migration: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/SpringJdbcSqlExecutorFactoryBean.java`

- [x] **Step 1: Replace the public contract test with connection-only roles**

```java
ConnectionHandle handle = new ConnectionHandle() {
    @Override public Connection connection() { return connection; }
    @Override public void close() { }
};
ConnectionHandleFactory factory = () -> handle;
TransactionCallback<String> callback = () -> "done";

assertSame(connection, factory.openHandle().connection());
assertEquals("done", callback.execute());
```

- [x] **Step 2: Add RED tests proving callbacks cannot directly complete transactions**

Use reflection assertions that `TransactionCallback.execute` has zero parameters and that `ConnectionHandle` exposes only `connection()` and `close()` beyond `Object` methods.

- [x] **Step 3: Run focused contract tests and confirm compilation fails**

Run: `mvn -pl lite-orm-core -Dtest=TransactionContractTest test`

Expected: FAIL because `ConnectionHandle` and `ConnectionHandleFactory` do not exist.

- [x] **Step 4: Add the new executor-facing contracts**

```java
public interface ConnectionHandle extends AutoCloseable {
    Connection connection();
    @Override void close();
}

@FunctionalInterface
public interface ConnectionHandleFactory {
    ConnectionHandle openHandle();
}

@FunctionalInterface
public interface TransactionCallback<T> {
    T execute();
}
```

- [x] **Step 5: Migrate `JdbcSqlExecutor` to `ConnectionHandleFactory`**

Replace `Transaction transaction` with `ConnectionHandle connectionHandle`, call `openHandle()`, call `connection()`, and close the handle after the statement and result set.

- [x] **Step 6: Make local transaction completion internal**

Keep `commit`, `rollback`, rollback-only state, connection restoration, and root transaction ownership on `SimpleTransaction`; do not expose those methods through a public executor-facing interface.

- [x] **Step 7: Change transaction callbacks to parameterless work**

```java
String result = assembly.transactionalExecutor().execute(() -> mapper.findName(id));
```

Nested callbacks join the current thread-bound transaction without receiving a handle.

- [x] **Step 8: Mechanically migrate the Spring connection handle**

Rename the Spring implementation roles as necessary so they implement `ConnectionHandle` and `ConnectionHandleFactory`; preserve existing `DataSourceUtils` acquisition/release behavior and add no new Spring feature.

- [x] **Step 9: Run focused transaction and JDBC tests**

Run: `mvn -pl lite-orm-core -Dtest=TransactionContractTest,SimpleTransactionTest,SimpleTransactionConcurrencyTest,TransactionDomainContractTest,JdbcSqlExecutorTest,LiteOrmAssemblyTest test`

Expected: PASS.

- [x] **Step 10: Run the full reactor**

Run: `mvn clean test`

Expected: PASS for core, starter, and examples.

- [x] **Step 11: Commit**

```bash
git add lite-orm-core lite-orm-spring-boot-starter lite-orm-examples docs/plans/liteorm-core-ga-implementation-plan.md
git commit -m "refactor: separate connection handles from transactions"
```

Implementation status: Completed and verified on 2026-08-16 with focused transaction/JDBC tests, the full reactor, and the external Maven processor fixture.

### Task 2: Complete Minimal Local Transaction Semantics

**Files:**
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/TransactionException.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/transaction/SimpleTransaction.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/transaction/SimpleTransactionalExecutor.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/transaction/SimpleTransactionTest.java`

- [x] **Step 1: Write RED nested rollback-only tests**

Cover nested success joining the root, nested failure marking the root rollback-only, the outer callback catching the original nested failure, rollback instead of commit, and an explicit rollback-only completion exception.

- [x] **Step 2: Run the focused transaction test**

Run: `mvn -pl lite-orm-core -Dtest=SimpleTransactionTest test`

Expected: FAIL because rollback-only completion does not exist.

- [x] **Step 3: Add rollback-only state to the internal transaction**

```java
void markRollbackOnly();
boolean isRollbackOnly();
```

Keep this state package-private. Do not add transaction options, propagation enums, savepoints, isolation configuration, or read-only configuration to core.

- [x] **Step 4: Mark the root rollback-only when nested work fails**

Wrap only the joined callback invocation. On `RuntimeException` or `Error`, call `markRollbackOnly()` and rethrow the original failure.

- [x] **Step 5: Prevent root commit after a caught nested failure**

If root work returns while rollback-only is set, roll back and throw `TransactionException` with a new `ROLLBACK_ONLY` type.

- [x] **Step 6: Run transaction tests and core tests**

Run: `mvn -pl lite-orm-core test`

Expected: PASS.

- [x] **Step 7: Commit**

```bash
git add lite-orm-core docs/plans/liteorm-core-ga-implementation-plan.md
git commit -m "fix: enforce local rollback-only semantics"
```

Implementation status: Completed on 2026-08-16. Core retains only minimal local JDBC transaction semantics; advanced transaction policies remain outside core.

---

## Phase G2: Execution Certainty and Observer Isolation

### Task 3: Define JDBC Execution Certainty

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/api/JdbcExecutionState.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/ExecutionOutcome.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/SqlExecutionException.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/jdbc/JdbcSqlExecutor.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/api/ExecutionObservationContractTest.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/jdbc/JdbcSqlExecutorTest.java`

- [x] **Step 1: Write RED JDBC-phase tests**

Cover connection, prepare, and bind failures before execution; exceptions thrown by `executeQuery`, `executeUpdate`, or `executeBatch`; result extraction failures after execute returns; and cleanup failures after execute returns.

- [x] **Step 2: Add executor-scoped certainty**

```java
public enum JdbcExecutionState {
    NOT_EXECUTED,
    OUTCOME_UNKNOWN,
    EXECUTED
}
```

Attach the state to both `ExecutionOutcome` and `SqlExecutionException`. `NOT_EXECUTED` means no JDBC execute method was invoked; `OUTCOME_UNKNOWN` means invocation began but did not return normally; `EXECUTED` means the JDBC execute method returned normally even if mapping, observation, or cleanup later failed.

Do not add `COMMITTED` or `ROLLED_BACK`: `JdbcSqlExecutor` does not own final transaction completion when participating in Spring or another host transaction manager.

- [x] **Step 3: Track JDBC lifecycle transitions explicitly**

Set `OUTCOME_UNKNOWN` immediately before invoking the JDBC execute method and `EXECUTED` immediately after it returns. Never describe mapping or cleanup failure as proof that SQL was not executed.

- [x] **Step 4: Run focused tests and core tests**

Run: `mvn -pl lite-orm-core -Dtest=ExecutionObservationContractTest,JdbcSqlExecutorTest test`

Expected: PASS.

- [x] **Step 5: Commit**

Commit: `feat: report jdbc completion certainty`

Implementation status: Completed on 2026-08-16. The state reports only JDBC execution certainty and deliberately does not claim transaction commit or rollback.

### Task 4: Isolate Terminal Interceptor Failures

**Files:**
- Modify: `lite-orm-core/src/main/java/org/liteorm/jdbc/JdbcSqlExecutor.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/jdbc/JdbcSqlExecutorTest.java`

- [x] **Step 1: Write RED tests for terminal callback isolation**

Verify every entered interceptor receives exactly one terminal callback, `afterSuccess` failures do not turn successful writes into SQL failures, remaining callbacks still run, and terminal observer failures are logged without parameter values.

- [x] **Step 2: Keep terminal observation non-configurable**

Use the JDK `System.Logger` owned internally by the executor. Do not add another public SPI or require a logging provider merely to observe failures from the existing observation SPI.

- [x] **Step 3: Make terminal notification non-throwing**

Catch every `afterSuccess` and `afterFailure` callback failure independently, continue reverse-order notification, and log interceptor type, statement ID, JDBC execution state, and callback failure without SQL parameters.

- [x] **Step 4: Keep `beforeExecution` veto semantics explicit**

A `beforeExecution` failure prevents JDBC acquisition, sends failure callbacks only to interceptors whose `beforeExecution` completed, and returns `NOT_EXECUTED` certainty.

- [x] **Step 5: Run core tests and commit**

Run: `mvn -pl lite-orm-core test`

Expected: PASS.

Commit: `fix: isolate interceptor terminal failures`

Implementation status: Completed on 2026-08-16. Interceptors remain optional observation plugins; core isolates their terminal callback failures without adding another public SPI.

---

## Phase G3: Bounded and Streaming Queries

### Task 5: Add Immutable Statement Options

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/api/StatementOptions.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/ExecutionPlan.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/BatchExecutionPlan.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/jdbc/JdbcSqlExecutor.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/jdbc/JdbcSqlExecutorTest.java`

- [x] **Step 1: Write RED validation and JDBC application tests**

Cover positive query timeout, positive fetch size, non-negative max rows, defaults that make no setter calls, and application before statement execution.

`maxRows` is only a JDBC result safety ceiling. It must not be documented or implemented as pagination; true pagination is expressed by dynamic parameters in the generated SQL.

- [x] **Step 2: Add immutable options**

```java
public record StatementOptions(Integer timeoutSeconds, Integer fetchSize, Integer maxRows) {
    public static StatementOptions defaults() {
        return new StatementOptions(null, null, null);
    }
}
```

- [x] **Step 3: Store options on execution plans**

All constructors defensively normalize `null` to defaults. Generated Mappers continue to use defaults; no Mapper annotation is added without a demonstrated application requirement.

- [x] **Step 4: Apply options to `PreparedStatement`**

Call `setQueryTimeout`, `setFetchSize`, and `setMaxRows` only for configured values.

- [x] **Step 5: Run core tests and commit**

Run: `mvn -pl lite-orm-core test`

Expected: PASS.

Commit: `feat: add jdbc statement controls`

Implementation status: Completed and verified on 2026-08-16 with focused executor/plan tests, all 136 core tests, and the full Maven reactor.

### Decision: Do Not Add Mapper `@Options`

- No current Mapper use case requires a permanent annotation for timeout, fetch size, or maximum-row caps.
- `StatementOptions` remains an immutable low-level execution-plan capability for direct executor composition and future integrations.
- Generated Mappers use defaults. Add XML attributes or a narrower declaration API only when a concrete production requirement demonstrates where configuration belongs.
- Do not copy MyBatis `@Options` merely for API similarity.

### Task 7: Add Scope-Bound Cursor Consumption

**Dependency:** Do not start this task until `SqlResult` ownership, column-label mapping, and Mapper return contracts are complete. Otherwise cursor support would create a second, premature typed-mapping path beside generated `Object[]` mapping.

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/api/RowCursor.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/CursorCallback.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/SqlExecutor.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/jdbc/JdbcSqlExecutor.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/CompilePipeline.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/FreemarkerCodeGenerator.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/jdbc/JdbcCursorExecutionTest.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/CursorCompilationTest.java`

- [ ] **Step 1: Write RED cursor lifecycle tests**

Verify rows are read one at a time, callback return closes result set/statement/handle, callback failure still closes resources, cursor access after callback fails, and no `List<Object[]>` materialization occurs.

- [ ] **Step 2: Define callback-scoped consumption**

```java
@FunctionalInterface
public interface CursorCallback<T, R> {
    R consume(RowCursor<T> cursor);
}

public interface RowCursor<T> {
    boolean next();
    T current();
}
```

Do not return `Stream<T>` because its lazy lifecycle can escape the executor boundary.

- [ ] **Step 3: Add a dedicated executor method**

```java
<T, R> R queryCursor(ExecutionPlan plan, CursorCallback<T, R> callback);
```

Require SELECT plans and a typed `RowMapper<T>`.

- [ ] **Step 4: Add generated Mapper support for callback cursor methods**

Support only an explicit callback parameter shape. Reject raw cursor returns and unsupported generic shapes at compile time.

- [ ] **Step 5: Run cursor, compiler, and core tests**

Run: `mvn -pl lite-orm-core test`

Expected: PASS.

- [ ] **Step 6: Commit**

Commit: `feat: add scope-bound cursor queries`

---

## Phase G4: Mapping and Mapper Method Contracts

**Execution priority:** Task 11 (`SqlResult` immutability) → Task 8 (column labels) → Task 9 (return shapes) → Task 10 (generated keys) → Task 7 (cursor consumption). Task numbers are retained to avoid rewriting completed-task references; this dependency order controls implementation.

### Task 8: Add Column-Label Result Metadata

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/annotation/Column.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/ResultColumn.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/SqlResult.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/jdbc/JdbcSqlExecutor.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/CompilePipeline.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/FreemarkerCodeGenerator.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/mapping/ColumnLabelMappingTest.java`

- [x] **Step 1: Write RED tests for reordered and aliased columns**

Verify Record components and JavaBean properties map by `ResultSetMetaData.getColumnLabel`, reordered SELECT lists remain correct, duplicate labels fail clearly, missing required labels fail clearly, and `@Column("user_id")` overrides the property name.

- [x] **Step 2: Capture immutable column metadata once per result**

Store normalized labels and JDBC indexes without exposing mutable metadata objects.

- [x] **Step 3: Generate label-based mappings**

Record components default to component names. JavaBean properties default to property names. Matching is case-insensitive but duplicate normalized labels are rejected.

- [x] **Step 4: Preserve explicit positional custom `RowMapper` behavior**

Custom row mappers continue to receive the live `ResultSet` and own vendor-specific mapping decisions.

- [x] **Step 5: Run mapping and core tests, then commit**

Run: `mvn -pl lite-orm-core test`

Expected: PASS.

Commit: `feat: map generated results by column label`

Implementation status: Completed and verified on 2026-08-16 with runtime-generated Record/JavaBean mapping tests, JDBC metadata tests, all 144 core tests, and the full Maven reactor.

### Task 9: Complete Supported Mapper Return Shapes

**Files:**
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/CompilePipeline.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/FreemarkerCodeGenerator.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/SqlParameterParser.java`
- Modify: `lite-orm-core/src/main/resources/templates/mapper-impl.ftl`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/MapperReturnContractCompilationTest.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/UnsupportedMapperSignatureCompilationTest.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/generated/GeneratedSourceGoldenTest.java`

- [x] **Step 1: Write RED return-contract tests**

Cover `T`, primitive scalar, `Optional<T>`, `List<T>`, update counts, batch counts, generated keys, default interface methods, inherited abstract Mapper methods, and rejection of raw/generic unresolved return shapes.

- [x] **Step 2: Define no-row behavior**

- Reference `T`: return `null`.
- `Optional<T>`: return `Optional.empty()`.
- Primitive scalar: throw `MappingException` identifying the Mapper method and expected primitive type.
- Single result with more than one row: throw `NonUniqueResultException`.

- [x] **Step 3: Generate `Optional<T>` without intermediate wrappers**

Generate `Optional.ofNullable(mappedValue)` after enforcing at-most-one-row semantics.

- [x] **Step 4: Support inherited abstract methods and preserve default methods**

Generate implementations for inherited Mapper methods exactly once. Do not override Java default methods unless they are explicitly SQL-annotated and supported.

- [x] **Step 5: Reject unresolved generic base methods**

Attach diagnostics to the concrete Mapper declaration or inherited method element with the unresolved type variable in the message.

- [x] **Step 6: Run compiler, golden, and core tests; commit**

Run: `mvn -pl lite-orm-core test`

Expected: PASS.

Commit: `feat: complete mapper return contracts`

Implementation status: Completed and verified on 2026-08-16 with RED/GREEN runtime compilation tests for reference, primitive, `Optional<T>`, `List<T>`, default methods, inherited resolved generic methods, unresolved generic diagnostics, all 146 core tests, and the full Maven reactor.

### Task 10: Complete Generated-Key Contracts

**Files:**
- Modify: `lite-orm-core/src/main/java/org/liteorm/annotation/GeneratedKey.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/SqlResult.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/jdbc/JdbcSqlExecutor.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/CompilePipeline.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/GeneratedKeyCompilationTest.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/jdbc/JdbcSqlExecutorTest.java`

- [ ] **Step 1: Write RED generated-key tests**

Cover `int`, `long`, boxed numeric types, `String`, UUID through a custom converter/row mapper, missing key rows, unexpected multiple key rows, and batch generated-key rejection.

- [ ] **Step 2: Convert supported key scalar types explicitly**

Reuse `ResultValueConverters`; do not cast arbitrary driver values directly.

- [ ] **Step 3: Keep composite and batch generated keys unsupported explicitly**

Reject unsupported declarations at compile time with a Mapper-method diagnostic rather than silently returning partial data.

- [ ] **Step 4: Run generated-key and core tests; commit**

Run: `mvn -pl lite-orm-core test`

Expected: PASS.

Commit: `feat: harden generated key contracts`

---

## Phase G5: API, Exceptions, Security, and Cleanup

### Task 11: Make `SqlResult` Deeply Immutable

**Files:**
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/SqlResult.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/api/SqlResultTest.java`

- [x] **Step 1: Write RED mutation tests**

Mutate the source list, source row arrays, returned list, returned row arrays, source batch counts, and returned batch counts. None may alter stored state.

- [x] **Step 2: Remove compatibility aliases**

Delete both `SqlResult.success(...)` methods without deprecation bridges.

- [x] **Step 3: Defensively copy query rows**

Use `List.copyOf` over cloned row arrays and return newly cloned row arrays from the query-results accessor.

- [x] **Step 4: Run API and core tests; commit**

Run: `mvn -pl lite-orm-core test`

Expected: PASS.

Commit: `refactor: make sql results deeply immutable`

Implementation status: Completed and verified on 2026-08-16 with focused mutation tests, all 139 core tests, and the full Maven reactor.

### Task 12: Normalize Exception Taxonomy and Redaction

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/api/ExecutionPhase.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/ConfigurationException.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/MappingException.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/NonUniqueResultException.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/SqlExecutionException.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/TransactionException.java`
- Test: `lite-orm-core/src/test/java/org/liteorm/test/api/ExceptionContractTest.java`

- [ ] **Step 1: Write RED hierarchy and redaction tests**

Verify every public LiteORM failure extends `LiteOrmException`, messages include statement IDs and phases, and messages do not contain bound parameter values, row values, passwords, URLs with credentials, or full configuration values.

- [ ] **Step 2: Remove unused exception types and enum values**

Delete exception classes or transaction types with no production creation path unless a task in this plan introduces that path.

- [ ] **Step 3: Replace row-bearing mapping exceptions**

Store target type, Mapper statement ID, column label/index, and cause. Do not retain or print complete row arrays.

- [ ] **Step 4: Preserve SQL text only through explicit diagnostics policy**

Default production exception messages use statement ID and phase. Expose SQL text only through an explicit opt-in diagnostic object, not the default message.

- [ ] **Step 5: Run API and core tests; commit**

Run: `mvn -pl lite-orm-core test`

Expected: PASS.

Commit: `refactor: harden liteorm exception contracts`

### Task 13: Remove Obsolete Public and Build Assets

**Files:**
- Modify or delete unused classes under: `lite-orm-core/src/main/java/org/liteorm`
- Verify: `lite-orm-core/src/main/resources/META-INF/services/javax.annotation.processing.Processor`
- Modify: `lite-orm-core/pom.xml`
- Modify: `docs/architecture/liteorm-architecture-review.md`
- Modify: `README.md`
- Modify: `README_cn.md`

- [ ] **Step 1: Add API-surface characterization**

Create a test that lists intended public API types and fails when implementation/compiler helpers leak into the supported runtime surface.

- [ ] **Step 2: Delete obsolete compatibility and implementation types**

Remove unused exceptions, old transaction names, stale comments, compatibility aliases, dead templates, `.disabled` services, and processor configuration that is not required by the external Maven fixture.

- [ ] **Step 3: Keep the active annotation processor service file**

The existing `META-INF/services/javax.annotation.processing.Processor` file remains if the external fixture proves it is the active bootstrap mechanism.

- [ ] **Step 4: Replace narrative print tests with assertions**

Search core tests for `System.out`, diagnostic dumps without assertions, and manual inspection expectations; convert them to focused assertions or delete them when redundant.

- [ ] **Step 5: Run external processor fixture and reactor**

Run: `mvn -pl lite-orm-core -am verify`

Expected: external Maven processor fixture PASS.

Run: `mvn clean test`

Expected: PASS.

- [ ] **Step 6: Commit**

Commit: `refactor: remove pre-ga core api debt`

---

## Phase G6: Production Database Verification

### Task 14: Add PostgreSQL and MySQL Compatibility Fixtures

**Files:**
- Modify: `pom.xml`
- Modify: `lite-orm-core/pom.xml`
- Create: `lite-orm-core/src/test/java/org/liteorm/test/database/AbstractDatabaseCompatibilityTest.java`
- Create: `lite-orm-core/src/test/java/org/liteorm/test/database/PostgresCompatibilityTest.java`
- Create: `lite-orm-core/src/test/java/org/liteorm/test/database/MySqlCompatibilityTest.java`
- Create: `lite-orm-core/src/test/resources/database/schema.sql`

- [ ] **Step 1: Add Testcontainers test dependencies**

Use pinned PostgreSQL and MySQL container images and JUnit 5 integration. Tests skip only when Docker is unavailable and CI must provide Docker for the GA gate.

- [ ] **Step 2: Define the shared database contract**

Run identical generated Mapper scenarios for scalar, Record, JavaBean, dynamic SQL, transactions, rollback-only, generated keys, batch, timeout, date/time, UUID or driver-equivalent type, binary data, and cursor consumption.

- [ ] **Step 3: Add driver-specific assertions only where JDBC behavior differs**

Keep SQL dialect differences in fixture SQL or Mapper declarations; do not put vendor branching in `JdbcSqlExecutor` unless JDBC itself cannot express the behavior.

- [ ] **Step 4: Run both compatibility suites**

Run: `mvn -pl lite-orm-core -Dtest=PostgresCompatibilityTest,MySqlCompatibilityTest test`

Expected: PASS with Docker available.

- [ ] **Step 5: Commit**

Commit: `test: verify postgres and mysql compatibility`

### Task 15: Add JDBC Failure and Concurrency Characterization

**Files:**
- Create: `lite-orm-core/src/test/java/org/liteorm/test/database/JdbcFailureCompatibilityTest.java`
- Create: `lite-orm-core/src/test/java/org/liteorm/test/database/CoreConcurrencySoakTest.java`
- Modify: `lite-orm-core/src/test/java/org/liteorm/test/jdbc/JdbcSqlExecutorTest.java`

- [ ] **Step 1: Add deterministic failure tests**

Cover `BatchUpdateException` update counts, connection acquisition failure, statement preparation failure, bind failure, execute failure, generated-key failure, result mapping failure, cancellation, timeout, commit failure, rollback failure, and close failure.

- [ ] **Step 2: Assert completion certainty for every failure phase**

Every case must assert `NOT_EXECUTED`, `EXECUTED`, `COMMITTED`, `ROLLED_BACK`, or `UNKNOWN` as appropriate.

- [ ] **Step 3: Add bounded concurrency soak coverage**

Run concurrent generated Mapper calls and transaction callbacks across independent DataSources for a fixed duration, verify no cross-thread connection reuse, no leaked transaction binding, and no corrupted result data.

- [ ] **Step 4: Run core verification and commit**

Run: `mvn -pl lite-orm-core verify`

Expected: PASS.

Commit: `test: characterize core jdbc failures`

---

## Phase G7: Documentation and Optimization Gate

### Task 16: Publish the Core GA Contract

**Files:**
- Modify: `README.md`
- Modify: `README_cn.md`
- Modify: `docs/architecture/liteorm-architecture-review.md`
- Create: `docs/core-ga-contract.md`
- Modify: `docs/plans/liteorm-core-ga-implementation-plan.md`

- [ ] **Step 1: Document supported Mapper contracts**

List supported SQL sources, parameter shapes, return shapes, mapping rules, generated-key limits, cursor lifecycle, statement options, and compile-time rejection behavior.

- [ ] **Step 2: Document transaction semantics**

Describe root and joined callbacks, rollback-only behavior, transaction options, DataSource domain boundaries, and unsupported savepoint/distributed transaction behavior.

- [ ] **Step 3: Document operational guarantees**

Describe thread safety, resource ownership, failure certainty, redaction, database compatibility matrix, and responsibilities intentionally left outside core.

- [ ] **Step 4: Run documentation example tests and reactor**

Run: `mvn clean test`

Expected: PASS.

- [ ] **Step 5: Commit**

Commit: `docs: define liteorm core ga contract`

### Task 17: Measure Before Optimizing

**Files:**
- Modify: `pom.xml`
- Create: `lite-orm-benchmarks/pom.xml`
- Create benchmark sources under: `lite-orm-benchmarks/src/main/java/org/liteorm/benchmark`
- Create: `docs/benchmarks/core-ga-baseline.md`

- [ ] **Step 1: Add JMH only after Tasks 1-16 pass**

Do not introduce caches or hot-path changes while adding the benchmark harness.

- [ ] **Step 2: Measure representative workloads**

Measure scalar query, Record mapping, JavaBean mapping, dynamic SQL, batch, generated keys, interceptor overhead, cursor consumption, and standalone transaction callbacks.

- [ ] **Step 3: Compare direct JDBC and documented MyBatis baselines**

Use identical schema, SQL, driver, JVM flags, warmup, measurement iterations, and result consumption.

- [ ] **Step 4: Profile allocations and CPU before proposing changes**

Record profiler evidence and reject optimizations without meaningful measured improvement.

- [ ] **Step 5: Keep accepted caches immutable or instance-scoped**

Reject global mutable compiler or runtime caches.

- [ ] **Step 6: Commit the baseline separately**

Commit: `perf: establish core ga benchmark baseline`

---

## Core GA Definition of Done

- [ ] Generated Mappers depend only on `SqlExecutor` and explicit cursor contracts.
- [ ] Executor-facing connection handles cannot commit or roll back application transactions.
- [ ] Nested local transaction failures mark the root rollback-only.
- [ ] Minimal local transaction commit, rollback, joined-callback, and rollback-only semantics remain tested without adding host transaction policies.
- [ ] Execution failures report completion certainty suitable for retry decisions.
- [ ] Terminal interceptor failures cannot convert successful writes into SQL failures.
- [ ] Queries support timeout, fetch size, maximum-row safety caps, and scope-bound cursor consumption.
- [ ] Dynamic Mapper parameters can produce bound, dialect-appropriate pagination SQL without a separate core pagination abstraction.
- [x] Record and JavaBean mapping use validated column labels rather than declaration position.
- [ ] `Optional<T>`, primitive no-row behavior, inheritance, and unsupported generic shapes are explicit.
- [ ] Generated-key behavior is typed and unsupported composite/batch forms fail at compile time.
- [x] `SqlResult` is deeply immutable and contains no compatibility aliases.
- [ ] Every public failure extends `LiteOrmException` and default messages redact values.
- [ ] Obsolete APIs, dead templates, disabled services, and narrative tests are removed.
- [ ] PostgreSQL and MySQL compatibility suites pass.
- [ ] JDBC failure-phase and concurrency characterization pass.
- [ ] Core GA documentation matches tested behavior.
- [ ] Optimization begins only after all correctness items are complete.
