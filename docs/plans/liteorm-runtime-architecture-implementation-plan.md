# LiteORM Runtime Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:test-driven-development` for every behavior change and `superpowers:executing-plans` to execute this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Every independently completed module must be committed with an English message and pushed immediately.

**Goal:** Replace the transitional runtime with a readable, thread-safe, transaction-correct `SqlExecutor` architecture that supports standalone, Spring-managed, and multiple-DataSource deployments without runtime Mapper proxies, reflective SQL dispatch, or ceremonial processor chains.

**Architecture:** Generated Mapper implementations depend only on the immutable execution-plan contract and the `SqlExecutor` interface. Each `JdbcSqlExecutor` belongs to exactly one transaction/DataSource domain and executes a fixed JDBC lifecycle; multiple DataSources are represented by multiple independent executor graphs and Mapper instances, not by hidden global routing or a DataSource key embedded in SQL plans. Stateful `Transaction` objects own connection acquisition, commit, rollback, timeout, and release semantics, while factories create execution-safe transaction handles for standalone or Spring participation.

**Tech Stack:** Java 21, Maven, JUnit 5, JDBC, `javax.sql.DataSource`, annotation processing, FreeMarker/Java source generation, H2, Spring Boot, Spring JDBC transactions.

---

## Plan Authority

- This is the only active LiteORM implementation plan.
- The deleted incremental and completion plans are obsolete and must not be restored.
- Existing implementation and tests may be deleted when their behavior is replaced by clearer executable contracts.
- Historical architecture documents describe prior decisions only; when they conflict with this plan, this plan wins.
- Implementation order is correctness first, architecture cleanup second, optimization last.

## Non-Negotiable Product Decisions

- Mapper implementations are generated during Java compilation and invoked as ordinary Java classes.
- XML overrides a SQL annotation only for the same Mapper method, and compilation emits a warning at that method.
- Supported OGNL-style expressions are translated into native Java; no runtime OGNL/MVEL/SpEL interpreter is introduced.
- `SqlExecutor` is the only generated Mapper-facing runtime execution interface.
- `SqlTask`, `BatchSqlTask`, and the `SqlEngine#execute(SqlTask)` compatibility path are deleted.
- Fixed JDBC phases are private implementation steps, not public processors or a configurable responsibility chain.
- `ExecutionContext` and string-keyed mutable execution attributes are deleted.
- `ConnectionProvider`, `ConnectionHandle`, `TransactionManager`, and `TransactionCoordinator` are not part of the target architecture.
- The public stateful transaction role is named `Transaction`.
- `Simple` prefixes describe the core module's minimal JDBC transaction implementation, not a deployment environment.
- Spring controls Spring transaction boundaries; core has no Spring dependency.
- Singleton Mapper implementations, executors, factories, and interceptors must be safe for concurrent calls.
- LiteORM closes JDBC resources it opens but does not implement a connection pool; the configured `DataSource` owns physical connection creation and pooling.
- Core does not provide distributed transactions or silently coordinate commits across DataSources.
- Multi-DataSource support is mandatory at three levels: assembly/package binding, compile-time Mapper/method binding, and typed dynamic routing for read/write, tenant, and shard scenarios.

## Target Runtime Model

```text
Generated MapperImpl (singleton-safe)
        |
        v
SqlExecutor.execute(ExecutionPlan)
        |
        v
JdbcSqlExecutor (one instance = one DataSource/transaction domain)
        |
        +--> ExecutionInterceptor.beforeExecution
        +--> TransactionFactory.openTransaction
        +--> Transaction.getConnection
        +--> prepare statement
        +--> bind ordered parameters
        +--> execute JDBC operation
        +--> extract SqlResult
        +--> ExecutionInterceptor.afterSuccess/afterFailure
        +--> close ResultSet
        +--> close PreparedStatement
        +--> Transaction.close
```

Public contracts:

```java
public interface SqlExecutor {
    SqlResult execute(ExecutionPlan plan);
}

public interface Transaction extends AutoCloseable {
    Connection getConnection();
    void commit();
    void rollback();
    @Override void close();
    default Integer getTimeoutSeconds() { return null; }
}

public interface TransactionFactory {
    Transaction openTransaction();
}
```

`TransactionFactory.openTransaction()` returns a per-execution handle. The handle itself knows whether it owns a temporary connection or participates in an already active transaction, so `JdbcSqlExecutor` always closes the returned handle and never branches on host-framework ownership.

Standalone explicit transactions use a core transaction boundary facade:

```java
public interface TransactionalExecutor {
    <T> T execute(TransactionCallback<T> callback);
}

@FunctionalInterface
public interface TransactionCallback<T> {
    T execute(Transaction transaction);
}
```

`SimpleTransactionalExecutor` binds one root `SimpleTransaction` to the current thread for the duration of the callback. Mapper calls made inside the callback receive lightweight participating transaction handles from `SimpleTransactionFactory`; calls outside a callback receive temporary auto-commit handles. Nested standalone transaction callbacks join the current transaction in the first implementation; propagation modes and savepoints are outside this plan.

## Multiple DataSource Design

- One `JdbcSqlExecutor` is permanently assembled with one `TransactionFactory`, and therefore one `DataSource` domain.
- `ExecutionPlan` contains statement behavior, not deployment routing. It does not contain a DataSource name, tenant key, shard key, or mutable route metadata.
- Multiple DataSources are configured by creating multiple named executor graphs:

```text
ordersDataSource -> ordersTransactionFactory -> ordersSqlExecutor -> OrdersMapperImpl
usersDataSource  -> usersTransactionFactory  -> usersSqlExecutor  -> UsersMapperImpl
```

- **Level 1 — assembly/package binding:** the same generated Mapper implementation may be instantiated more than once with different qualified `SqlExecutor` instances. Spring selects the executor through explicit package rules and bean names; core selects it through explicit construction.
- **Level 2 — compile-time static binding:** LiteORM provides `@UseDataSource("users")` on Mapper types and methods. A method annotation overrides the Mapper annotation, which overrides the assembly default. The compiler emits one constructor dependency per distinct static executor and generates a direct field call; it does not inspect annotations on the SQL hot path.
- Generated static bindings use LiteORM-owned constructor parameter metadata such as `@ExecutorRef("users")`. The Spring registrar reads that metadata only during startup and injects the matching named `SqlExecutor`; generated code has no Spring dependency.
- A Mapper with only one default DataSource keeps the single `SqlExecutor` constructor established in R1. A Mapper referencing multiple static DataSources receives multiple `SqlExecutor` constructor parameters in deterministic key order.
- **Level 3 — typed dynamic routing:** read/write separation, tenant routing, and sharding use `@UseDataSource(provider = TenantRouteProvider.class)`. The provider implementation and input type are validated at compilation time and invoked through an ordinary Java call from generated code.
- Dynamic routing uses an immutable `SqlExecutorRegistry` and performs one key lookup after the provider returns. Static routes never pay this lookup cost.
- Dynamic providers receive typed route input plus immutable statement metadata so read/write routing can inspect statement type without parsing SQL:

```java
public interface DataSourceKeyProvider<P> {
    String select(DataSourceSelection<P> selection);
}

public record DataSourceSelection<P>(
        String statementId,
        ExecutionPlan.StatementType statementType,
        P parameter) {
}

public interface SqlExecutorRegistry {
    SqlExecutor require(String dataSourceKey);
}
```

- A dynamic provider Mapper method accepts zero or one route input. Multiple values must be wrapped in a record so provider invocation remains strongly typed and statically generated.
- `@UseDataSource` cannot declare both a fixed value and a provider. Blank keys, inaccessible providers, incompatible generic types, missing provider constructors, and unknown statically configured executor keys fail with actionable diagnostics.
- The selected executor key is resolved exactly once per Mapper invocation and cannot change between parameter binding, execution, and result extraction.
- An active local transaction is bound to one executor/DataSource key. Selecting another key fails before connection acquisition; LiteORM never silently opens an unrelated connection inside that transaction.
- Read/write routing is transaction-aware: once a transaction is active on the writer key, reads in that transaction remain pinned to the same writer executor.
- Spring validates configured DataSource resources at invocation/assembly boundaries. If a transaction is active for one configured DataSource and a Mapper selects another without an external multi-resource transaction coordinator, execution fails clearly instead of pretending both calls share one transaction.
- Cross-DataSource work uses two explicit transaction boundaries. Atomic distributed commit requires an external transaction system and is not implemented by core.
- User-defined routing policy may be packaged as a higher-level executor/registry decorator, but it must not mutate `ExecutionPlan`, move a stateful `Transaction` between executors, or make `JdbcSqlExecutor` aware of tenant/shard policy.

## Error and Cleanup Contract

- Validate null plans, blank statement IDs, blank SQL, missing parameters, invalid binder counts, and unsupported statement/result combinations before preparing JDBC statements.
- Preserve the original execution failure as the primary exception.
- Attach interceptor unwind failures and JDBC cleanup failures with `Throwable#addSuppressed` in occurrence order.
- Close resources in physical reverse order: `ResultSet`, `PreparedStatement`, transaction handle.
- Successful SQL followed by cleanup failure is reported as a LiteORM execution/transaction exception; it is never silently ignored.
- `commit`, `rollback`, and `close` are idempotent only where the concrete transaction contract explicitly guarantees it; misuse otherwise fails with a clear `TransactionException`.

## Delivery Protocol

Every module below follows this exact loop:

1. Add or update a focused test that fails for the intended reason.
2. Run only that test and record the expected RED failure.
3. Implement the smallest coherent production change.
4. Run the focused test, affected module tests, then `mvn clean test` when the module boundary is complete.
5. Review the diff for obsolete compatibility code and misleading names.
6. Commit with the listed English message.
7. Run `git push` immediately.

Do not combine independently reviewable modules into one commit.

---

## Phase R0: Restore a Trustworthy Baseline

### Module R0.1: Remove the Abandoned Lifecycle Attempt

**Files:**
- Restore: `lite-orm-core/src/main/java/org/liteorm/DefaultSqlEngine.java`
- Restore: `lite-orm-core/src/main/java/org/liteorm/LiteOrm.java`
- Restore: `lite-orm-core/src/main/java/org/liteorm/StandaloneSqlEngine.java`
- Restore: `lite-orm-core/src/test/java/org/liteorm/test/LiteOrmAssemblyTest.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/JdbcExecutionLifecycle.java`
- Verify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/LiteOrmAutoConfiguration.java`

- [x] Restore only the uncommitted interrupted refactor to commit `7dad969`; keep this plan and README changes.
- [x] Run `git diff --check` and verify no accidental source changes remain.
- [x] Run `mvn clean test` and capture any real baseline failures, including the Spring `.processors(...)` mismatch if it remains after restoration.
- [x] Fix only baseline compilation drift required to return to the last pushed architecture.
- [x] Run `mvn clean test` and require a green reactor.
- [x] Commit with `chore: restore runtime refactor baseline` if a source fix was required; otherwise do not create an empty commit.
- [x] Push the commit immediately when one exists.

**Completion criteria:** The repository builds from a clean, known baseline before the replacement architecture starts.

---

## Phase R1: Replace the Generated-Code Runtime Contract

### Module R1.1: Introduce `SqlExecutor`

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/api/SqlExecutor.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/FreemarkerCodeGenerator.java`
- Modify generated-code assertions under: `lite-orm-core/src/test/java/org/liteorm/test`
- Delete after migration: `lite-orm-core/src/main/java/org/liteorm/api/SqlEngine.java`

- [x] Add a generated-source test asserting the Mapper implementation has one constructor dependency: `SqlExecutor`.
- [x] Run the focused generator test and verify it fails because generated code still imports `SqlEngine`.
- [x] Add `SqlExecutor#execute(ExecutionPlan)` and migrate generated constructors, fields, imports, and fixtures.
- [x] Delete `SqlEngine` after all production and test references compile against `SqlExecutor`.
- [x] Assert generated source contains no `SqlEngine`, `DefaultSqlEngine`, `ConnectionProvider`, or runtime processor imports.
- [x] Run core generator tests and `mvn clean test`.
- [x] Commit with `refactor: replace sql engine with sql executor`.
- [x] Push immediately.

**Completion criteria:** Generated Mappers depend on a role-oriented execution interface and no longer know connection or engine assembly details.

### Module R1.2: Make Execution Plans Immutable and Delete Tasks

**Files:**
- Replace: `lite-orm-core/src/main/java/org/liteorm/api/ExecutionPlan.java`
- Replace: `lite-orm-core/src/main/java/org/liteorm/api/BatchExecutionPlan.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/api/SqlTask.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/api/BatchSqlTask.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/FreemarkerCodeGenerator.java`
- Modify runtime and generator tests under: `lite-orm-core/src/test/java/org/liteorm/test`

- [x] Add contract tests for immutable ordered parameters, binder alignment, generated-key metadata, row mapper metadata, statement type, and SQL source.
- [x] Verify RED because current plans expose parameter maps, transaction hints, and task compatibility.
- [x] Replace the interface/legacy implementations with final immutable plan types using defensive copies.
- [x] Remove `getParameterMap()`, `usesParameterMap()`, `requiresTransaction()`, and unused runtime-only result-type strings.
- [x] Generate `ExecutionPlan` or `BatchExecutionPlan` directly for annotation, XML, dynamic SQL, provider, generated-key, and batch methods.
- [x] Delete every `SqlTask` compatibility branch and update tests to use plans directly.
- [x] Run provider, binder, mapping, generated-key, batch, generator, and full reactor tests.
- [x] Commit with `refactor: replace legacy sql tasks with immutable plans`.
- [x] Push immediately.

**Completion criteria:** Runtime input is immutable, ordered, minimal, and contains no transaction ownership or deployment-routing policy.

---

## Phase R2: Establish Transaction Semantics

### Module R2.1: Define the Transaction Contracts

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/api/Transaction.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/TransactionFactory.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/TransactionCallback.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/TransactionalExecutor.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/TransactionException.java`
- Add tests: `lite-orm-core/src/test/java/org/liteorm/test/transaction/TransactionContractTest.java`

- [ ] Write compile-time/API tests for the exact signatures documented in Target Runtime Model.
- [ ] Verify RED because the roles do not exist.
- [ ] Add the four narrow contracts without implementation-specific methods or DataSource identifiers.
- [ ] Make transaction failures unchecked LiteORM exceptions so generated Mapper calls do not leak checked infrastructure exceptions.
- [ ] Document that a factory-created transaction handle owns its own close semantics and is never shared across concurrent calls.
- [ ] Run focused API tests and core tests.
- [ ] Commit with `feat: define transaction execution contracts`.
- [ ] Push immediately.

**Completion criteria:** Transaction behavior is represented by explicit domain roles rather than connection-provider and manager/coordinator abstractions.

### Module R2.2: Implement `SimpleTransaction`

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/transaction/SimpleTransaction.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/transaction/SimpleTransactionFactory.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/transaction/SimpleTransactionalExecutor.java`
- Add tests: `lite-orm-core/src/test/java/org/liteorm/test/transaction/SimpleTransactionTest.java`
- Add tests: `lite-orm-core/src/test/java/org/liteorm/test/transaction/SimpleTransactionConcurrencyTest.java`

- [ ] Write RED tests for lazy `DataSource#getConnection`, auto-commit restoration, commit, rollback, close, commit failure followed by rollback, and suppressed cleanup failures.
- [ ] Write RED tests proving a transaction callback reuses one connection across multiple Mapper/executor calls on the same thread.
- [ ] Write RED tests proving calls outside a transaction callback obtain and close independent auto-commit handles.
- [ ] Write RED concurrency tests proving two threads never observe each other's bound root transaction.
- [ ] Implement a private thread-bound scope inside the simple transaction package; do not expose a mutable context object.
- [ ] Make nested callbacks join the current root transaction and let only the outer callback commit, roll back, and release it.
- [ ] Roll back the root transaction when the callback throws; preserve rollback/close failures as suppressed exceptions.
- [ ] Remove the thread binding in every terminal path.
- [ ] Run focused transaction tests and core tests.
- [ ] Commit with `feat: implement simple jdbc transactions`.
- [ ] Push immediately.

**Completion criteria:** Core provides correct local transactions without managing a pool or leaking transaction state between calls or threads.

---

## Phase R3: Implement the Fixed JDBC Executor

### Module R3.1: Add `JdbcSqlExecutor`

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/jdbc/JdbcSqlExecutor.java`
- Create package-private helpers under: `lite-orm-core/src/main/java/org/liteorm/jdbc`
- Migrate tests from: `lite-orm-core/src/test/java/org/liteorm/test/runtime`

- [ ] Write RED tests for SELECT, INSERT, UPDATE, DELETE, batch, generated keys, null binding, custom binders, and custom row mappers.
- [ ] Write RED tests for validation before connection acquisition.
- [ ] Write RED tests for exact close order: `ResultSet`, statement, transaction handle.
- [ ] Write RED tests for primary exception preservation and suppressed interceptor/cleanup failures.
- [ ] Implement the lifecycle as explicit private methods in one readable call stack.
- [ ] Keep physical phases non-public and non-reorderable; do not create `SqlProcessor` replacements.
- [ ] Obtain a fresh per-call transaction handle from `TransactionFactory`, call `getConnection()`, and always close the handle.
- [ ] Keep executor fields immutable and require interceptor implementations to be thread-safe.
- [ ] Run all runtime characterization tests and core tests.
- [ ] Commit with `feat: add fixed jdbc sql executor`.
- [ ] Push immediately.

**Completion criteria:** All JDBC behavior passes through one thread-safe executor with deterministic ownership and cleanup.

### Module R3.2: Move Observability to Typed Interceptors

**Files:**
- Create or modify typed interceptors under: `lite-orm-core/src/main/java/org/liteorm/interceptor`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/ExecutionInvocation.java`
- Add tests under: `lite-orm-core/src/test/java/org/liteorm/test/interceptor`
- Delete later in this module: `lite-orm-core/src/main/java/org/liteorm/runtime/LoggingProcessor.java`
- Delete later in this module: `lite-orm-core/src/main/java/org/liteorm/runtime/SlowQueryMonitorProcessor.java`
- Delete later in this module: `lite-orm-core/src/main/java/org/liteorm/runtime/SqlAuditProcessor.java`

- [ ] Add RED tests for ordered before callbacks and reverse success/failure unwind.
- [ ] Add RED tests that logging, slow-query, and audit observers receive immutable invocation data and cannot mutate SQL or parameters.
- [ ] Implement logging, slow-query, and audit behavior as `ExecutionInterceptor` implementations.
- [ ] Keep authorization/routing observation possible through typed read-only metadata without generic attribute maps.
- [ ] Delete the three processor implementations after equivalent behavior is covered.
- [ ] Run interceptor, runtime, and full core tests.
- [ ] Commit with `refactor: move execution observers to interceptors`.
- [ ] Push immediately.

**Completion criteria:** Cross-cutting observation uses a genuine ordered interceptor pattern; physical JDBC work does not.

---

## Phase R4: Assemble Core Without Ambiguous Containers

### Module R4.1: Replace Transitional Runtime Assembly

**Files:**
- Create or replace: `lite-orm-core/src/main/java/org/liteorm/LiteOrm.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/DefaultSqlEngine.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/StandaloneSqlEngine.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/JdbcConnectionProvider.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/LocalTransactionCoordinator.java`
- Modify: `lite-orm-core/src/test/java/org/liteorm/test/LiteOrmAssemblyTest.java`

- [ ] Write RED assembly tests for creating `JdbcSqlExecutor` and `SimpleTransactionalExecutor` from one `DataSource` and ordered interceptors.
- [ ] Define `LiteOrm.jdbc(DataSource)` as a small builder/factory for one executor graph; do not return a roleless runtime container.
- [ ] Expose the built `SqlExecutor` and standalone `TransactionalExecutor` explicitly.
- [ ] Reject null DataSources, null interceptors, and duplicate interceptor instances with clear configuration errors.
- [ ] Delete old engine, standalone, connection-provider, and transaction-coordinator implementations after all call sites migrate.
- [ ] Run assembly, runtime, transaction, and full reactor tests.
- [ ] Commit with `refactor: simplify core runtime assembly`.
- [ ] Push immediately.

**Completion criteria:** Public assembly names describe the components users receive; no class is named for a deployment mode instead of its domain responsibility.

---

## Phase R5: Integrate Spring Transactions

### Module R5.1: Implement `SpringTransaction`

**Files:**
- Create: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/SpringTransaction.java`
- Create: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/SpringTransactionFactory.java`
- Delete: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/SpringConnectionProvider.java`
- Add tests: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot/SpringTransactionTest.java`

- [ ] Write RED tests that `getConnection()` delegates to `DataSourceUtils` semantics and reuses Spring's thread-bound connection.
- [ ] Write RED tests that commit and rollback are no-ops while Spring manages the active transaction.
- [ ] Write RED tests that calls outside Spring transactions release their connection through `DataSourceUtils`.
- [ ] Implement the Spring transaction facade with lazy connection acquisition and ownership-aware close behavior.
- [ ] Ensure core remains free of Spring classes.
- [ ] Delete `SpringConnectionProvider` after assembly migrates.
- [ ] Run starter transaction tests and `mvn clean test`.
- [ ] Commit with `feat: integrate spring transaction factory`.
- [ ] Push immediately.

**Completion criteria:** Spring owns boundary timing while LiteORM receives a transaction handle with the same executor-facing contract as core.

### Module R5.2: Update Spring Mapper Assembly

**Files:**
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/LiteOrmAutoConfiguration.java`
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/GeneratedMapperBeanDefinitionRegistrar.java`
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/LiteOrmProperties.java`
- Modify tests under: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot`

- [ ] Write RED tests that generated Mapper beans receive `SqlExecutor`, not `SqlEngine` or connection abstractions.
- [ ] Replace processor assembly with `JdbcSqlExecutor(SpringTransactionFactory, interceptors)`.
- [ ] Preserve ordered interceptor collection and explicit startup diagnostics.
- [ ] Verify Mapper calls join `@Transactional` and non-transactional calls release resources.
- [ ] Verify singleton Mapper/executor use remains safe under concurrent Spring calls.
- [ ] Run starter tests and `mvn clean test`.
- [ ] Commit with `refactor: assemble spring mappers with sql executor`.
- [ ] Push immediately.

**Completion criteria:** Spring auto-configuration assembles the same executor contract without inheriting standalone transaction APIs.

---

## Phase R6: Support Multiple DataSources

### Module R6.1: Verify Core Multi-DataSource Isolation

**Files:**
- Add: `lite-orm-core/src/test/java/org/liteorm/test/multidatasource/MultiDataSourceExecutionTest.java`
- Modify core assembly only if tests expose a missing explicit hook.

- [ ] Create two H2 DataSources with different schemas and identically named Mapper interfaces.
- [ ] Instantiate the same generated Mapper implementation twice, each with its own `JdbcSqlExecutor` and `SimpleTransactionFactory`.
- [ ] Verify reads and writes never cross DataSource boundaries.
- [ ] Verify concurrent transactions on both executors do not share connections or thread-bound roots.
- [ ] Verify a failure in one DataSource does not roll back or close the other DataSource's transaction.
- [ ] Keep `ExecutionPlan` free of DataSource keys and routing metadata.
- [ ] Run the focused isolation test and core tests.
- [ ] Commit with `test: verify multi datasource executor isolation`.
- [ ] Push immediately.

**Completion criteria:** Multiple DataSources work through composition of independent executor graphs with no hidden global state.

### Module R6.2: Add Assembly and Package-Level Static Binding

**Files:**
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/LiteOrmProperties.java`
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/GeneratedMapperBeanDefinitionRegistrar.java`
- Add: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot/PackageDataSourceBindingTest.java`
- Update: `docs/extensions.md`

- [ ] Write RED Spring context tests with `usersDataSource/usersSqlExecutor` and `ordersDataSource/ordersSqlExecutor`.
- [ ] Define explicit package-to-executor rules such as `com.example.user -> usersSqlExecutor`; do not select a primary DataSource silently when more than one executor exists.
- [ ] Require an unambiguous executor bean name for every configured Mapper package.
- [ ] Register single-DataSource Mapper beans with the configured qualified `SqlExecutor` constructor argument.
- [ ] Verify the same Mapper interface can be registered under distinct explicit bean names when intentionally used against two DataSources.
- [ ] Fail startup for missing executor names, duplicate Mapper bean names, overlapping ambiguous package rules, or multiple unqualified executors.
- [ ] Verify each `@Transactional(transactionManager = "...")` boundary uses the matching DataSource executor.
- [ ] Run multi-DataSource starter tests and `mvn clean test`.
- [ ] Commit with `feat: support package datasource binding`.
- [ ] Push immediately.

**Completion criteria:** Ordinary multi-DataSource applications bind Mapper packages or instances explicitly with no runtime routing overhead.

### Module R6.3: Compile Mapper and Method Static DataSource Bindings

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/annotation/UseDataSource.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/annotation/ExecutorRef.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/MapperCompilationModel.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/CompilePipeline.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/FreemarkerCodeGenerator.java`
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/GeneratedMapperBeanDefinitionRegistrar.java`
- Add tests under: `lite-orm-core/src/test/java/org/liteorm/test/multidatasource/staticbinding`
- Add: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot/StaticDataSourceAnnotationBindingTest.java`

- [ ] Write RED compiler tests for Mapper-level `@UseDataSource("users")` and method-level `@UseDataSource("archive")`.
- [ ] Define precedence as method annotation, then Mapper annotation, then assembly/package default.
- [ ] Reject blank keys, conflicting fixed/provider declarations, and invalid annotation placement during compilation.
- [ ] Collect distinct fixed keys per Mapper and sort them deterministically for generated constructor parameters.
- [ ] Keep a Mapper with only the assembly default on the existing single `SqlExecutor` constructor.
- [ ] Generate one final `SqlExecutor` field per distinct explicit key and annotate its constructor parameter with LiteORM-owned `@ExecutorRef("key")` metadata.
- [ ] Generate direct method calls such as `archiveSqlExecutor.execute(plan)`; do not use a registry lookup for fixed routes.
- [ ] Ensure generated code does not call `getAnnotation`, Spring AOP, `AbstractRoutingDataSource`, SpEL, or a ThreadLocal route context.
- [ ] Update the Spring registrar to resolve `@ExecutorRef` metadata at startup and inject the matching named executor without adding Spring annotations to generated classes.
- [ ] Fail Spring startup when a referenced executor key is missing or resolves to multiple beans.
- [ ] Verify a Mapper type default can be overridden by one method without affecting sibling methods.
- [ ] Verify XML-over-SQL-annotation precedence remains independent from DataSource precedence.
- [ ] Run focused compiler/Spring tests and `mvn clean test`.
- [ ] Commit with `feat: compile static datasource bindings`.
- [ ] Push immediately.

**Completion criteria:** Static class/method DataSource selection becomes ordinary generated Java field dispatch with zero hot-path annotation inspection.

### Module R6.4: Add Typed Dynamic DataSource Providers

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/api/DataSourceKeyProvider.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/DataSourceSelection.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/SqlExecutorRegistry.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/runtime/ImmutableSqlExecutorRegistry.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/annotation/UseDataSource.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/MapperCompilationModel.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/CompilePipeline.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/FreemarkerCodeGenerator.java`
- Add tests under: `lite-orm-core/src/test/java/org/liteorm/test/multidatasource/dynamicrouting`

- [ ] Write RED compiler tests for `@UseDataSource(provider = TenantRouteProvider.class)` with zero and one Mapper argument.
- [ ] Define `DataSourceSelection<P>` as immutable statement ID, statement type, and typed route argument.
- [ ] Require multiple routing inputs to be wrapped in one record; reject multi-parameter provider methods with an actionable diagnostic.
- [ ] Validate provider visibility, generic input compatibility, accessible no-arg constructor, and nonblank returned key.
- [ ] Generate one final provider instance and one `SqlExecutorRegistry` dependency only for Mappers that use dynamic routing.
- [ ] Generate a direct provider call followed by exactly one `registry.require(key)` lookup before execution.
- [ ] Reject unknown keys through a dedicated configuration/routing exception that includes Mapper statement ID and selected key.
- [ ] Keep `ExecutionPlan` free of DataSource keys, tenant values, shard values, and routing policy.
- [ ] Add a read/write provider fixture that selects writer for INSERT/UPDATE/DELETE and reader for SELECT outside transactions.
- [ ] Add tenant and shard fixtures that select executors from a typed record input.
- [ ] Assert generated source contains no reflection, runtime annotation parsing, OGNL, SpEL, or SQL-text parsing for routing.
- [ ] Verify the immutable registry is safe for concurrent singleton Mapper use.
- [ ] Run focused provider, generator, concurrency, and core tests.
- [ ] Commit with `feat: add typed datasource routing providers`.
- [ ] Push immediately.

**Completion criteria:** Dynamic read/write, tenant, and shard selection is an explicit typed escape hatch without restoring reflective Mapper dispatch.

### Module R6.5: Enforce Transaction-Safe Routing

**Files:**
- Modify transaction scope types under: `lite-orm-core/src/main/java/org/liteorm/transaction`
- Modify: `lite-orm-core/src/main/java/org/liteorm/runtime/ImmutableSqlExecutorRegistry.java`
- Modify Spring transaction/routing types under: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot`
- Add: `lite-orm-core/src/test/java/org/liteorm/test/multidatasource/MultiDataSourceTransactionRoutingTest.java`
- Add: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot/MultiDataSourceTransactionRoutingTest.java`

- [ ] Write RED tests proving a local transaction opened for `users` rejects routing to `orders` before `ordersDataSource#getConnection()` is called.
- [ ] Bind the active simple transaction to an immutable executor/DataSource key as well as the current thread.
- [ ] Pin read/write routing to the writer executor for every call inside an active writer transaction.
- [ ] Verify nested callbacks cannot switch DataSource keys and always release the original binding on commit, rollback, callback failure, and cleanup failure.
- [ ] Verify two threads can hold transactions for different keys concurrently without observing each other's route or connection.
- [ ] In Spring, inspect configured transaction-bound DataSource resources and reject selection of a different configured DataSource unless an explicitly supplied external multi-resource coordinator owns the boundary.
- [ ] Verify `@Transactional(transactionManager = "usersTransactionManager")` uses `usersSqlExecutor` and rejects an `orders` method/provider route in the same boundary.
- [ ] Verify separate explicit Spring transaction boundaries can independently commit work to two DataSources without claiming atomicity.
- [ ] Preserve the original SQL/transaction failure and attach route validation or cleanup failures as suppressed exceptions where applicable.
- [ ] Document that XA/JTA/Seata-style atomic coordination belongs to an external integration, not core.
- [ ] Run focused transaction, concurrency, Spring, and full reactor tests.
- [ ] Commit with `feat: enforce datasource transaction boundaries`.
- [ ] Push immediately.

**Completion criteria:** No static or dynamic route can silently escape the active transaction's DataSource domain.

---

## Phase R7: Delete Runtime Debt

### Module R7.1: Remove Processor Chain and Mutable Context

**Files:**
- Delete: `lite-orm-core/src/main/java/org/liteorm/ExecutionContext.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/runtime/SqlProcessor.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/runtime/ConnectionProcessor.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/runtime/ParameterProcessor.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/runtime/ExecutionProcessor.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/runtime/ResultProcessor.java`
- Delete obsolete tests under: `lite-orm-core/src/test/java/org/liteorm/test`

- [ ] Add an architecture test asserting public APIs and constructors contain no `ExecutionContext` or `SqlProcessor` references.
- [ ] Verify RED before deletion.
- [ ] Delete the processor chain and mutable context after equivalent JDBC/interceptor tests are green.
- [ ] Delete tests that only assert obsolete processor ordering or print demonstrations.
- [ ] Run `rg "ExecutionContext|SqlProcessor|ConnectionProcessor|ParameterProcessor|ExecutionProcessor|ResultProcessor"` and require no production matches.
- [ ] Run `mvn clean test`.
- [ ] Commit with `refactor: remove legacy runtime processor chain`.
- [ ] Push immediately.

**Completion criteria:** The runtime lifecycle is readable from `JdbcSqlExecutor` without tracing mutable state through handlers.

### Module R7.2: Remove Obsolete Transaction and Configuration Types

**Files:**
- Delete: `lite-orm-core/src/main/java/org/liteorm/api/ConnectionProvider.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/api/TransactionContext.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/api/TransactionCoordinator.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/api/TransactionOperations.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/LiteOrmConfig.java`
- Delete obsolete tests and documentation references.

- [ ] Add an architecture test asserting the obsolete types are absent from generated source and public constructors.
- [ ] Delete the types and migrate the final remaining references to `Transaction`, `TransactionFactory`, `TransactionalExecutor`, and `SqlExecutor`.
- [ ] Search all modules and docs for the deleted type names.
- [ ] Run `mvn clean test`.
- [ ] Commit with `refactor: remove obsolete transaction abstractions`.
- [ ] Push immediately.

**Completion criteria:** Connection and transaction ownership have one vocabulary across core and Spring.

### Module R7.3: Remove Dead Build and Generation Assets

**Files:**
- Review/delete: `lite-orm-core/src/main/resources/templates/mapper-impl.ftl`
- Review/delete: `lite-orm-core/src/main/resources/META-INF/services/javax.annotation.processing.Processor.disabled`
- Modify: `lite-orm-core/pom.xml`
- Modify: `pom.xml`

- [ ] Add a clean external Maven compilation fixture that consumes the built processor artifact.
- [ ] Verify which generator path is active and delete the unused FreeMarker template if generated code is assembled programmatically.
- [ ] Delete the `.disabled` service file because it is not a Java service registration.
- [ ] Retain `<proc>none</proc>` for the processor module's self-compilation only if the external fixture proves it is required to avoid processor self-loading; otherwise remove it.
- [ ] Remove unused dependencies only after `mvn dependency:analyze` and source searches prove they are unnecessary.
- [ ] Update module descriptions that still advertise a responsibility-chain runtime.
- [ ] Run external fixture compilation and `mvn clean test`.
- [ ] Commit with `chore: remove obsolete build and generation assets`.
- [ ] Push immediately.

**Completion criteria:** Build configuration and resources reflect the actual processor bootstrap and generator implementation.

---

## Phase R8: Generated-Code Readability and Diagnostics

### Module R8.1: Review Generated Mapper Source

**Files:**
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/FreemarkerCodeGenerator.java`
- Modify compiler models under: `lite-orm-core/src/main/java/org/liteorm/compile`
- Add golden-source tests under: `lite-orm-core/src/test/java/org/liteorm/test/generated`

- [ ] Add golden tests for annotation SQL, XML override, dynamic conditions, foreach, provider, binder, row mapper, batch, and generated keys.
- [ ] Require stable imports, descriptive local names, compact methods, statement IDs, and source comments that identify Mapper methods without embedding unstable absolute paths.
- [ ] Split generated helper methods when one Mapper method's renderer/binding code becomes difficult to read.
- [ ] Keep generated code free of reflection, runtime expression parsing, generic maps for ordered JDBC parameters, and framework internals.
- [ ] Verify javac diagnostics point to the Mapper method/XML statement for unsupported behavior.
- [ ] Run generator tests, external fixture compilation, and `mvn clean test`.
- [ ] Commit with `refactor: improve generated mapper readability`.
- [ ] Push immediately.

**Completion criteria:** Generated Java is a first-class debuggable artifact rather than an opaque compiler by-product.

---

## Phase R9: Documentation and Architecture Review

### Module R9.1: Update User Documentation

**Files:**
- Modify: `README.md`
- Modify: `README_cn.md`
- Modify: `docs/extensions.md`
- Modify: `docs/migration-guide.md`
- Modify: `docs/mybatis-compatibility.md`
- Replace or update: `docs/architecture/liteorm-architecture-review.md`
- Modify examples under: `lite-orm-examples/basic-mapper`

- [ ] Document `SqlExecutor`, `Transaction`, `TransactionFactory`, simple callback transactions, Spring transaction participation, and resource ownership.
- [ ] Document single-DataSource standalone and Spring examples.
- [ ] Document explicit multi-DataSource construction, qualifiers, transaction-manager matching, and the absence of core distributed transactions.
- [ ] Remove all examples using `SqlEngine`, processors, `ConnectionProvider`, `TransactionCoordinator`, `StandaloneSqlEngine`, or legacy tasks.
- [ ] Document extension guidance: typed provider/binder/row mapper/interceptor first; decorator-based routing only for exceptional dynamic routing.
- [ ] Run documentation link searches and example builds.
- [ ] Commit with `docs: describe sql executor runtime architecture`.
- [ ] Push immediately.

**Completion criteria:** User documentation matches actual public APIs and clearly explains transaction and multi-DataSource behavior.

### Module R9.2: Perform the Final Architecture Review

**Files:**
- Replace: `docs/architecture/liteorm-architecture-review.md`
- Update this plan's checkboxes and completion notes.

- [ ] Review compile-time input, model, generation, runtime execution, standalone assembly, Spring assembly, and multi-DataSource assembly end to end.
- [ ] Review SRP, OCP, LSP, ISP, and DIP with concrete class dependencies.
- [ ] Review justified patterns only: Strategy for transaction implementations, Factory for transaction creation, Template/explicit lifecycle inside the executor, Interceptor for observation, Adapter for Spring JDBC semantics, Builder only for assembly, Decorator only for optional routing.
- [ ] Explicitly reject responsibility-chain usage for fixed JDBC phases.
- [ ] Review public API size, package naming, exception taxonomy, concurrency guarantees, test readability, and generated-source readability.
- [ ] Record remaining optimization work separately; do not mix benchmarks or caches into correctness changes.
- [ ] Run `mvn clean test` and all external examples.
- [ ] Commit with `docs: complete runtime architecture review`.
- [ ] Push immediately.

**Completion criteria:** The final architecture is understandable from component names and dependency direction without relying on historical compatibility knowledge.

---

## Phase R10: Optimization Gate

### Module R10.1: Measure Before Optimizing

**Files:**
- Add benchmark module only after all previous phases are complete.
- Add benchmark documentation under: `docs/benchmarks`

- [ ] Define representative generated Mapper workloads for scalar query, record mapping, JavaBean mapping, dynamic SQL, batch, and Spring transaction participation.
- [ ] Compare allocations and throughput against direct JDBC and a documented MyBatis baseline.
- [ ] Profile before changing generated code or runtime internals.
- [ ] Accept optimizations only when behavior tests remain green and the measured improvement is meaningful.
- [ ] Keep caches instance-scoped or immutable; reject global mutable compiler/runtime caches.
- [ ] Commit each independently measured optimization with its benchmark evidence and push immediately.

**Completion criteria:** Optimization decisions are based on reproducible measurements, not assumed reflection costs or pattern preferences.

---

## Global Definition of Done

- [ ] Generated Mapper hot paths use no runtime proxy, reflective method dispatch, runtime XML parsing, or runtime expression engine.
- [ ] XML-over-annotation precedence remains method-scoped and emits a compiler warning.
- [ ] `SqlExecutor` is the only generated Mapper-facing runtime execution contract.
- [ ] `Transaction` is the single connection/commit/rollback/close abstraction.
- [ ] Standalone and Spring transaction implementations pass the same lifecycle characterization suite.
- [ ] Multiple DataSources are isolated through explicit executor graphs and qualifiers.
- [ ] Package/Mapper static binding, method-level static override, and typed dynamic routing providers are all implemented and documented.
- [ ] Fixed `@UseDataSource` routes compile to direct executor field calls with no hot-path annotation reflection or registry lookup.
- [ ] Dynamic read/write, tenant, and shard routes use typed providers, one registry lookup, and transaction-domain validation.
- [ ] Cross-DataSource calls cannot silently escape an active local or Spring transaction boundary.
- [ ] Fixed JDBC phases are not configurable processors.
- [ ] Runtime and compiler singletons are concurrency-safe.
- [ ] Obsolete APIs, tests, templates, service placeholders, and docs are removed.
- [ ] `mvn clean test` and external Mapper examples pass from a clean checkout.
- [ ] Every completed module has an English commit and has been pushed.
