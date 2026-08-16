# LiteORM Runtime Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:test-driven-development` for every behavior change and `superpowers:executing-plans` to execute this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Every independently completed module must be committed with an English message and pushed immediately.

**Goal:** Replace the transitional runtime with a readable, thread-safe, transaction-correct `SqlExecutor` architecture that supports standalone, Spring-managed, and multiple-DataSource deployments without runtime Mapper proxies, reflective SQL dispatch, or ceremonial processor chains.

**Architecture:** Generated Mapper implementations depend only on the immutable execution-plan contract and the `SqlExecutor` interface. Each generated Mapper instance and `JdbcSqlExecutor` belongs to exactly one transaction/DataSource domain and executes a fixed JDBC lifecycle; an application may contain multiple disjoint Mapper groups for multiple DataSources, but the same Mapper interface is never registered against multiple DataSources by the Starter. Stateful `Transaction` objects own connection acquisition, commit, rollback, timeout, and release semantics, while factories create execution-safe transaction handles for standalone or Spring participation.

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
- First-stage multi-DataSource support binds each Mapper package and Mapper interface to exactly one named Spring DataSource bean; different DataSources use disjoint Mapper groups, and dynamic routing inside one binding is delegated to the configured DataSource implementation.

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

- A Mapper package is statically bound to one named Spring `DataSource` bean through
  `lite-orm.mapper-bindings[].data-source`.
- The binding remains explicit even when the application has only one DataSource. The Starter does
  not infer a default DataSource, infer Mapper packages from the Spring Boot application package, or
  enable zero-configuration Mapper registration.
- The configured value may identify a physical connection pool or an application-provided routing
  `DataSource` such as `AbstractRoutingDataSource` or dynamic-datasource.
- Starter resolves the DataSource bean and uses `SpringTransactionFactory` plus core
  `JdbcAssembly` to create the Mapper-facing `SqlExecutor`.
- LiteORM does not create another dynamic-routing system, inspect third-party `@DS` annotations,
  infer master/slave roles, or maintain a routing `ThreadLocal`.
- `ExecutionPlan` contains statement behavior only and remains free of DataSource and routing data.

```text
physical DataSource or routing DataSource
        -> SpringTransactionFactory
        -> core JdbcAssembly
        -> SqlExecutor
        -> generated MapperImpl
```

- Each generated Mapper interface is registered once by the Starter and resolves to one `SqlExecutor`
  and one DataSource domain. The Starter does not create multiple Spring Mapper beans for the same
  interface and does not expose bean-name prefixes for that purpose.
- Generated Mapper implementations remain container-neutral plain Java classes. The compiler does not
  add Spring `@Component`, `@Repository`, injection, qualifier, or conditional annotations; Spring
  registration remains entirely inside the Starter.
- Dynamic read/write, tenant, or shard selection belongs to the configured DataSource implementation.
  LiteORM treats routing and physical DataSources identically.
- Method-level fixed DataSource annotations are deferred until a concrete requirement remains after
  package binding and external routing DataSource support.

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

- [x] Write compile-time/API tests for the exact signatures documented in Target Runtime Model.
- [x] Verify RED because the roles do not exist.
- [x] Add the four narrow contracts without implementation-specific methods or DataSource identifiers.
- [x] Make transaction failures unchecked LiteORM exceptions so generated Mapper calls do not leak checked infrastructure exceptions.
- [x] Document that a factory-created transaction handle owns its own close semantics and is never shared across concurrent calls.
- [x] Run focused API tests and core tests.
- [x] Commit with `feat: define transaction execution contracts`.
- [x] Push immediately.

**Completion criteria:** Transaction behavior is represented by explicit domain roles rather than connection-provider and manager/coordinator abstractions.

### Module R2.2: Implement `SimpleTransaction`

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/transaction/SimpleTransaction.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/transaction/SimpleTransactionFactory.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/transaction/SimpleTransactionalExecutor.java`
- Add tests: `lite-orm-core/src/test/java/org/liteorm/test/transaction/SimpleTransactionTest.java`
- Add tests: `lite-orm-core/src/test/java/org/liteorm/test/transaction/SimpleTransactionConcurrencyTest.java`

- [x] Write RED tests for lazy `DataSource#getConnection`, auto-commit restoration, commit, rollback, close, commit failure followed by rollback, and suppressed cleanup failures.
- [x] Write RED tests proving a transaction callback reuses one connection across multiple Mapper/executor calls on the same thread.
- [x] Write RED tests proving calls outside a transaction callback obtain and close independent auto-commit handles.
- [x] Write RED concurrency tests proving two threads never observe each other's bound root transaction.
- [x] Implement a private thread-bound scope inside the simple transaction package; do not expose a mutable context object.
- [x] Make nested callbacks join the current root transaction and let only the outer callback commit, roll back, and release it.
- [x] Roll back the root transaction when the callback throws; preserve rollback/close failures as suppressed exceptions.
- [x] Remove the thread binding in every terminal path.
- [x] Run focused transaction tests and core tests.
- [x] Commit with `feat: implement simple jdbc transactions`.
- [x] Push immediately.

**Completion criteria:** Core provides correct local transactions without managing a pool or leaking transaction state between calls or threads.

---

## Phase R2.5: Complete the Core Role Foundation

> **Priority gate:** The obsolete engine/processor runtime is removed first. Do not implement or
> migrate `JdbcSqlExecutor`, Spring transaction integration, or generated DataSource dispatch until
> every remaining R2.5 module is complete. This phase fixes the
> role model, dependency direction, ownership, and assembly boundaries before lifecycle code.

### Module R2.5.1: Remove Legacy Runtime and Finalize Observation Contracts

**Files:**
- Delete: `lite-orm-core/src/main/java/org/liteorm/DefaultSqlEngine.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/StandaloneSqlEngine.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/ExecutionContext.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/LiteOrm.java`
- Delete: obsolete connection, coordinator, processor-chain, and global configuration types
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/ExecutionInterceptor.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/api/ExecutionInvocation.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/ExecutionOutcome.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/api/SqlExecutionException.java`
- Add tests: `lite-orm-core/src/test/java/org/liteorm/test/api/ExecutionObservationContractTest.java`
- Add tests: `lite-orm-core/src/test/java/org/liteorm/test/architecture/LegacyRuntimeRemovalTest.java`

- [x] Write a RED architecture test enumerating every obsolete engine, processor, mutable context, connection-provider, coordinator, and global configuration type.
- [x] Delete the old runtime closure and tests instead of maintaining compatibility adapters.
- [x] Use the existing immutable `ExecutionPlan` directly for `beforeExecution`; delete the redundant `ExecutionInvocation` model.
- [x] Represent terminal metrics/failure with a separate immutable `ExecutionOutcome`.
- [x] Remove the generic routing metadata map because DataSource routing has dedicated typed roles and occurs before SQL execution.
- [x] Define callback order and failure semantics in Javadoc: `beforeExecution` in registration order, terminal callbacks in reverse order, callback failures never replace an earlier SQL failure.
- [x] Keep parameters defensively copied and ensure exception messages never include parameter values.
- [x] Remove the old Spring default executor/connection-provider assembly; retain generated Mapper registration until Spring transaction roles are implemented.
- [x] Delete obsolete runtime integration tests and keep compiler/transaction contract tests.
- [x] Run focused architecture/API tests and `mvn clean test`.
- [x] Commit with `refactor: remove legacy runtime architecture`.
- [x] Push immediately.

**Completion criteria:** The repository contains only the compile-time Mapper contracts, immutable execution data, transaction roles, and read-only observation foundation; no old runtime path remains executable.

### Module R2.5.2: Define Multi-DataSource Roles (Superseded)

> **Historical note:** This module was implemented before the first-stage boundary was clarified.
> Phase R6 proved that package-to-DataSource binding plus an application-provided routing
> `DataSource` covers the required integration without LiteORM-owned routing contracts. Module
> R6.3 removes these premature APIs; they are not part of the target architecture.

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/api/DataSourceKeyProvider.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/DataSourceSelection.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/SqlExecutorRegistry.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/DataSourceRoutingException.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/annotation/UseDataSource.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/annotation/ExecutorRef.java`
- Add tests: `lite-orm-core/src/test/java/org/liteorm/test/api/MultiDataSourceContractTest.java`

- [x] Write RED compile-time/API tests for fixed type keys, method override metadata, typed provider selection, immutable selection metadata, and registry lookup.
- [x] Define `DataSourceSelection<P>` as statement ID, statement type, and one typed route input; do not include SQL text, mutable maps, or transaction state.
- [x] Define `DataSourceKeyProvider<P>` as one ordinary Java strategy call returning an executor key.
- [x] Define `SqlExecutorRegistry` as one read-only `require(statementId, dataSourceKey)` lookup so unknown-key diagnostics retain statement identity; mutation belongs only to assembly-time implementation.
- [x] Define `@UseDataSource` so fixed-key and provider modes are mutually exclusive and compiler-validatable.
- [x] Define LiteORM-owned `@ExecutorRef` constructor-parameter metadata for startup injection without a Spring dependency.
- [x] Add a dedicated routing exception containing statement ID and selected key but no SQL parameters.
- [x] Run focused contract/compiler tests and `mvn -pl lite-orm-core test`.
- [x] Commit with `feat: define multi datasource roles`.
- [x] Push immediately.

**Historical completion criteria:** These contracts were implemented and tested, then superseded by
the simpler package-to-DataSource architecture in Phase R6.

### Module R2.5.3: Define Transaction-Domain Safety

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/api/TransactionDomain.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/TransactionDomainGuard.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/transaction/SimpleTransactionFactory.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/transaction/SimpleTransactionalExecutor.java`
- Add tests: `lite-orm-core/src/test/java/org/liteorm/test/transaction/TransactionDomainContractTest.java`

- [x] Write RED tests defining immutable nonblank domain identity and rejection before a second DataSource connection is acquired.
- [x] Keep `Transaction` free of deployment-routing APIs; domain validation surrounds factory/executor selection rather than JDBC operations.
- [x] Define one narrow guard role that validates the selected executor domain against the active transaction domain.
- [x] Share one explicitly assembled guard/scope across simple factories; do not use a process-global routing `ThreadLocal`.
- [x] Expose the same `TransactionDomainGuard` role for future direct executor and registry-based dynamic validation.
- [x] Prove independent assemblies and concurrent threads cannot observe each other's active domain.
- [x] Run focused transaction/concurrency tests and `mvn -pl lite-orm-core test`.
- [x] Commit with `feat: enforce transaction domain boundaries`.
- [x] Push immediately.

**Completion criteria:** Cross-DataSource safety is a first-class transaction invariant for independently assembled executor graphs.

### R2.5 Foundation Gate

- [x] `SqlExecutor`, immutable plans/results, read-only observation, transaction contracts, and transaction-domain safety exist.
- [x] No role combines SQL execution, connection ownership, transaction boundary control, routing, or dependency injection.
- [x] Public names describe domain responsibility; no new `Standalone*`, `Local*`, generic `*Engine`, mutable context, or processor-chain role exists.
- [x] Generated Mappers depend only on `SqlExecutor`; simple transactions depend on `DataSource`; transaction-domain roles do not depend on compiler or Spring types.
- [x] `mvn clean test` passes from a clean checkout.

Assembly remains in Phase R4 because its concrete product includes `JdbcSqlExecutor`. Defining an
assembly container before that executor exists would create a placeholder abstraction and invert the
real dependency order.

```text
Generated Mapper -> SqlExecutor <- JdbcSqlExecutor (Phase R3)
                          |              |
                    ExecutionPlan       +-> TransactionFactory -> Transaction -> DataSource
                                         +-> TransactionDomainGuard
                                         +-> ExecutionInterceptor

Mapper package -> named DataSource -> SpringTransactionFactory -> JdbcSqlExecutor
```

---

## Phase R3: Implement the Fixed JDBC Executor

### Module R3.1: Add `JdbcSqlExecutor`

**Files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/jdbc/JdbcSqlExecutor.java`
- Create package-private helpers under: `lite-orm-core/src/main/java/org/liteorm/jdbc`
- Migrate tests from: `lite-orm-core/src/test/java/org/liteorm/test/runtime`

- [x] Write RED tests for SELECT, INSERT, UPDATE, DELETE, batch, generated keys, null binding, custom binders, and custom row mappers.
- [x] Write RED tests for validation before connection acquisition.
- [x] Write RED tests for exact close order: `ResultSet`, statement, transaction handle.
- [x] Write RED tests for primary exception preservation and suppressed interceptor/cleanup failures.
- [x] Implement the lifecycle as explicit private methods in one readable call stack.
- [x] Keep physical phases non-public and non-reorderable; do not create `SqlProcessor` replacements.
- [x] Obtain a fresh per-call transaction handle from `TransactionFactory`, call `getConnection()`, and always close the handle.
- [x] Keep executor fields immutable and require interceptor implementations to be thread-safe.
- [x] Run all replacement runtime tests and core tests.
- [x] Commit with `feat: add fixed jdbc sql executor`.
- [x] Push immediately.

**Completion criteria:** All JDBC behavior passes through one thread-safe executor with deterministic ownership and cleanup.

### Module R3.2: Move Observability to Typed Interceptors

**Files:**
- Create or modify typed interceptors under: `lite-orm-core/src/main/java/org/liteorm/interceptor`
- Use: `lite-orm-core/src/main/java/org/liteorm/api/ExecutionPlan.java`
- Use: `lite-orm-core/src/main/java/org/liteorm/api/ExecutionOutcome.java`
- Add tests under: `lite-orm-core/src/test/java/org/liteorm/test/interceptor`

- [x] Add RED tests for ordered before callbacks and reverse success/failure unwind.
- [x] Add RED tests that logging, slow-query, and audit observers receive immutable plan/outcome data and cannot mutate SQL or parameters.
- [x] Implement logging, slow-query, and audit behavior as `ExecutionInterceptor` implementations.
- [x] Keep policy observation possible through typed read-only metadata without generic attribute maps or routing state.
- [x] The obsolete processor implementations were already deleted in Module R2.5.1.
- [x] Run interceptor, runtime, and full core tests.
- [x] Commit with `refactor: move execution observers to interceptors`.
- [x] Push immediately.

**Completion criteria:** Cross-cutting observation uses a genuine ordered interceptor pattern; physical JDBC work does not.

---

## Phase R4: Assemble Core Without Ambiguous Containers

### Module R4.1: Replace Transitional Runtime Assembly

**Files:**
- Create or replace: `lite-orm-core/src/main/java/org/liteorm/LiteOrm.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/JdbcAssembly.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/DefaultSqlEngine.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/StandaloneSqlEngine.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/JdbcConnectionProvider.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/LocalTransactionCoordinator.java`
- Modify: `lite-orm-core/src/test/java/org/liteorm/test/LiteOrmAssemblyTest.java`

- [x] Write RED assembly tests for creating `JdbcSqlExecutor` and `SimpleTransactionalExecutor` from one `DataSource` and ordered interceptors.
- [x] Define `LiteOrm.jdbc(DataSource)` as a small builder/factory for one executor graph; do not return a roleless runtime container.
- [x] Expose the built `SqlExecutor` and standalone `TransactionalExecutor` explicitly.
- [x] Reject null DataSources, null interceptors, and duplicate interceptor instances with clear configuration errors.
- [x] Delete old engine, standalone, connection-provider, and transaction-coordinator implementations after all call sites migrate.
- [x] Run assembly, runtime, transaction, and full reactor tests.
- [x] Commit with `refactor: simplify core runtime assembly`.
- [x] Push immediately.

**Completion criteria:** Public assembly names describe the components users receive; no class is named for a deployment mode instead of its domain responsibility.

---

## Phase R5: Integrate Spring Transactions

### Module R5.1: Implement `SpringTransaction`

**Files:**
- Create: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/SpringTransaction.java`
- Create: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/SpringTransactionFactory.java`
- Delete: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/SpringConnectionProvider.java`
- Add tests: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot/SpringTransactionTest.java`

- [x] Write RED tests that `getConnection()` delegates to `DataSourceUtils` semantics and reuses Spring's thread-bound connection.
- [x] Write RED tests that commit and rollback are no-ops while Spring manages the active transaction.
- [x] Write RED tests that calls outside Spring transactions release their connection through `DataSourceUtils`.
- [x] Implement the Spring transaction facade with lazy connection acquisition and ownership-aware close behavior.
- [x] Ensure core remains free of Spring classes.
- [x] Delete `SpringConnectionProvider` after assembly migrates.
- [x] Run starter transaction tests and `mvn clean test`.
- [x] Commit with `feat: integrate spring transaction factory`.
- [x] Push immediately.

**Completion criteria:** Spring owns boundary timing while LiteORM receives a transaction handle with the same executor-facing contract as core.

### Module R5.2: Update Spring Mapper Assembly

**Files:**
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/LiteOrmAutoConfiguration.java`
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/GeneratedMapperBeanDefinitionRegistrar.java`
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/LiteOrmProperties.java`
- Modify tests under: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot`

- [x] Write RED tests that generated Mapper beans receive `SqlExecutor`, not `SqlEngine` or connection abstractions.
- [x] Replace processor assembly with `JdbcSqlExecutor(SpringTransactionFactory, interceptors)`.
- [x] Preserve ordered interceptor collection and explicit startup diagnostics.
- [x] Verify Mapper calls join `@Transactional` and non-transactional calls release resources.
- [x] Verify singleton Mapper/executor use remains safe under concurrent Spring calls.
- [x] Run starter tests and `mvn clean test`.
- [x] Commit with `refactor: assemble spring mappers with sql executor`.
- [x] Push immediately.

**Completion criteria:** Spring auto-configuration assembles the same executor contract without inheriting standalone transaction APIs.

---

## Phase R6: Support Multiple DataSources

### Module R6.1: Verify Core Multi-DataSource Isolation

**Files:**
- Add: `lite-orm-core/src/test/java/org/liteorm/test/multidatasource/MultiDataSourceExecutionTest.java`
- Modify core assembly only if tests expose a missing explicit hook.

- [x] Create two H2 DataSources with different schemas and identically named Mapper interfaces.
- [x] Instantiate the same generated Mapper implementation twice, each with its own `JdbcSqlExecutor` and `SimpleTransactionFactory`.
- [x] Verify reads and writes never cross DataSource boundaries.
- [x] Verify concurrent transactions on both executors do not share connections or thread-bound roots.
- [x] Verify a failure in one DataSource does not roll back or close the other DataSource's transaction.
- [x] Keep `ExecutionPlan` free of DataSource keys and routing metadata.
- [x] Run the focused isolation test and core tests.
- [x] Commit with `test: verify multi datasource executor isolation`.
- [x] Push immediately.

**Completion criteria:** Multiple DataSources work through composition of independent executor graphs with no hidden global state.

### Module R6.2: Bind Mapper Packages to Spring DataSources

**Files:**
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/LiteOrmProperties.java`
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/GeneratedMapperBeanDefinitionRegistrar.java`
- Add: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/SpringJdbcSqlExecutorFactoryBean.java`
- Add: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot/PackageDataSourceBindingTest.java`
- Update: `docs/extensions.md`

- [x] Write RED Spring context tests with `userDataSource` and `storeDataSource` beans.
- [x] Define each binding as exactly `package-name + data-source`; do not add prefixes, `*-ref`, router, or executor fields.
- [x] Resolve `data-source` strictly as a named Spring `DataSource` bean and fail startup when missing or incompatible.
- [x] Use `SpringTransactionFactory` and core `JdbcAssembly` to create one reusable executor per referenced DataSource.
- [x] Register Mapper beans with the assembled executor while keeping generated constructors unchanged.
- [x] Verify disjoint Mapper packages can bind to different DataSources while every Mapper interface remains in one DataSource domain.
- [x] Verify an `AbstractRoutingDataSource` is accepted exactly like a physical DataSource.
- [x] Fail startup for duplicate Mapper bean names and overlapping package rules.
- [x] Verify each `@Transactional(transactionManager = "...")` boundary uses the matching DataSource executor.
- [x] Run multi-DataSource starter tests and `mvn clean test`.
- [x] Commit with `feat: support package datasource binding`.
- [x] Push immediately.

**Completion criteria:** Mapper packages bind to physical or routing Spring DataSources without LiteORM owning dynamic routing.

### Module R6.3: Remove Premature LiteORM Routing Contracts

**Files:**
- Delete: `lite-orm-core/src/main/java/org/liteorm/api/DataSourceKeyProvider.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/api/DataSourceSelection.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/api/SqlExecutorRegistry.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/api/DataSourceRoutingException.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/annotation/UseDataSource.java`
- Delete: `lite-orm-core/src/main/java/org/liteorm/annotation/ExecutorRef.java`
- Delete: `lite-orm-core/src/test/java/org/liteorm/test/api/MultiDataSourceContractTest.java`
- Modify: `lite-orm-core/src/test/java/org/liteorm/test/architecture/LegacyRuntimeRemovalTest.java`

- [x] Add a RED architecture assertion that the premature routing contracts are absent.
- [x] Verify production code has no references to the routing contracts.
- [x] Delete the unused APIs, annotations, and their obsolete contract tests.
- [x] Keep package binding and `TransactionDomainGuard` behavior unchanged.
- [x] Run focused core tests and `mvn clean test`.
- [x] Commit with `refactor: remove premature datasource routing contracts`.
- [x] Push immediately.

**Completion criteria:** First-stage multi-DataSource support has one clear ownership model: LiteORM
binds Mapper packages, while the configured `DataSource` owns any dynamic routing.

### Module R6.4: Enforce Spring Transaction/DataSource Alignment

**Files:**
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/SpringTransaction.java`
- Modify: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot/SpringTransactionTest.java`

- [x] Add a RED test that an active transaction for DataSource A cannot acquire DataSource B.
- [x] Reject the mismatch before DataSource B opens a connection or executes SQL.
- [x] Keep non-transactional calls and matching Spring transaction participation unchanged.
- [x] Keep routing DataSources valid when the transaction manager and Mapper binding use the same routing DataSource bean.
- [x] Run focused Spring transaction tests and `mvn clean test`.
- [x] Commit with `fix: enforce spring datasource transaction alignment`.
- [x] Push immediately.

**Completion criteria:** A Mapper cannot silently escape an active single-DataSource Spring
transaction and auto-commit work through another configured DataSource.

### Module R6.5: Enforce One Mapper to One DataSource

**Priority:** Complete this contract correction before Module R7.3.

**Files:**
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/LiteOrmProperties.java`
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/GeneratedMapperBeanDefinitionRegistrar.java`
- Modify: `lite-orm-spring-boot-starter/src/main/resources/application.yml`
- Modify tests under: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot`
- Modify: `README.md`
- Modify: `README_cn.md`
- Modify: `docs/extensions.md`

- [x] Add a RED Spring context test proving one Mapper interface is registered once with the default Bean name derived from the Mapper interface through `Introspector.decapitalize` semantics.
- [x] Add RED configuration tests proving duplicate or overlapping package bindings fail even when the package names are identical.
- [x] Remove `bean-name-prefix` from `LiteOrmProperties.MapperBinding`, configuration metadata/examples, documentation, and test helpers.
- [x] Delete the same-generated-Mapper/two-DataSource Spring registration path and its prefixed Bean-name assertions.
- [x] Keep `mapper-bindings[].package-name + data-source` mandatory for single- and multiple-DataSource applications; do not add implicit package scanning or automatic single-DataSource selection.
- [x] Keep application-level multi-DataSource support through disjoint Mapper package bindings, where every Mapper interface resolves to exactly one named DataSource and one executor graph.
- [x] Keep generated `*MapperImpl` classes free of Spring `@Component`, `@Repository`, `@Autowired`, `@Qualifier`, and conditional annotations; Spring integration remains a Starter-only responsibility.
- [x] Continue registering generated implementations through `BeanDefinitionRegistryPostProcessor`, injecting the one `SqlExecutor` assembled for the Mapper package's configured DataSource.
- [x] Run focused Starter registration tests, all Starter tests, and `mvn clean test`.
- [x] Commit with `refactor: enforce one datasource per spring mapper`.
- [x] Push immediately.

**Completion criteria:** Every Spring Mapper interface has one stable default Bean name, one generated
implementation instance, one executor, and one DataSource domain. Generated code remains independent
of Spring, and multiple application DataSources are represented only by disjoint Mapper groups or by
an application-provided routing DataSource behind a single explicit binding. Single-DataSource
applications use the same explicit package-to-DataSource configuration and have no separate implicit
registration mode.

### Deferred: LiteORM-Owned Dynamic Routing

- [x] Do not add LiteORM SQL-type, read/write, tenant, or shard routing during the first stage.
- [x] Accept physical and routing Spring DataSource implementations through the same `data-source` binding.
- [ ] Re-evaluate method-level static DataSource binding only after a concrete use case cannot be expressed by package binding or the configured routing DataSource.
- [ ] Revisit a core routing SPI only if DataSource-level routing cannot satisfy a demonstrated requirement.

---

## Phase R7: Delete Runtime Debt

### Module R7.1: Remove Processor Chain and Mutable Context

- [x] Completed early in Module R2.5.1 so the new foundation cannot depend on the processor chain or mutable context.
- [x] `LegacyRuntimeRemovalTest` prevents the deleted classes from returning.
- [x] Obsolete processor-order and JDBC-engine tests were deleted; behavior will be rebuilt against `JdbcSqlExecutor` in Phase R3.

**Completion criteria:** The runtime lifecycle is readable from `JdbcSqlExecutor` without tracing mutable state through handlers.

### Module R7.2: Remove Obsolete Transaction and Configuration Types

- [x] Completed early in Module R2.5.1 together with the obsolete engines that consumed these types.
- [x] Core connection ownership now uses `Transaction`; boundary control uses `TransactionalExecutor`.
- [x] Spring's obsolete connection-provider implementation and default engine assembly were removed pending the new Spring transaction adapter.
- [x] The unused global mutable `LiteOrmConfig` singleton was deleted.

**Completion criteria:** Connection and transaction ownership have one vocabulary across core and Spring.

### Module R7.3: Remove Dead Build and Generation Assets

**Priority:** Start only after Module R6.5 fixes the public Spring Mapper registration contract.

**Files:**
- Add the independent external fixture under: `lite-orm-examples/external-maven-processor` and keep it outside the root Maven reactor.
- Review/retain or delete: `lite-orm-core/src/main/resources/templates/mapper-impl.ftl`
- Verify absent: `lite-orm-core/src/main/resources/META-INF/services/javax.annotation.processing.Processor.disabled`
- Modify: `lite-orm-core/pom.xml`
- Modify: `pom.xml`

- [x] Add a Maven Invoker fixture with no reactor parent or relative-path dependency; it must consume the installed `lite-orm-core` processor artifact like an external application.
- [x] Run the external fixture before build cleanup and verify it compiles a Mapper, executes the processor, and produces the expected `*MapperImpl` source.
- [x] Verify the active generator path before deleting resources. Retain `mapper-impl.ftl` while `FreemarkerCodeGenerator` loads it; delete it only if a tested replacement removes that runtime dependency.
- [x] Verify the obsolete `.disabled` service file remains absent and that only the valid `javax.annotation.processing.Processor` registration is packaged.
- [x] Retain `<proc>none</proc>` for the processor module's self-compilation only if the external fixture proves it is required to avoid processor self-loading; otherwise remove it.
- [x] Remove unused dependencies only after `mvn dependency:analyze` and source searches prove they are unnecessary.
- [x] Update module descriptions that still advertise a responsibility-chain runtime.
- [x] Run the Maven Invoker fixture, focused processor tests, `mvn dependency:analyze`, and `mvn clean test`.
- [x] Commit with `chore: remove obsolete build and generation assets`.
- [x] Push immediately.

**Completion criteria:** Build configuration and resources reflect the actual processor bootstrap and generator implementation.

---

## Phase R8: Generated-Code Readability and Diagnostics

### Module R8.1: Review Generated Mapper Source

**Files:**
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/FreemarkerCodeGenerator.java`
- Modify compiler models under: `lite-orm-core/src/main/java/org/liteorm/compile`
- Add golden-source tests under: `lite-orm-core/src/test/java/org/liteorm/test/generated`

- [x] Add golden tests for annotation SQL, XML override, dynamic conditions, foreach, provider, binder, row mapper, batch, and generated keys.
- [x] Require stable imports, descriptive local names, compact methods, statement IDs, and source comments that identify Mapper methods without embedding unstable absolute paths.
- [x] Split generated helper methods when one Mapper method's renderer/binding code becomes difficult to read.
- [x] Keep generated code free of reflection, runtime expression parsing, generic maps for ordered JDBC parameters, and framework internals.
- [x] Verify javac diagnostics point to the Mapper method/XML statement for unsupported behavior.
- [x] Run generator tests, external fixture compilation, and `mvn clean test`.
- [x] Commit with `refactor: improve generated mapper readability`.
- [x] Push immediately.

Completion notes:
- Shared golden-source tests cover stable imports/comments plus annotation and XML dynamic rendering; focused compilation fixtures continue to cover provider, binder, row mapper, batch, and generated-key source shapes.
- Dynamic `foreach`, `choose`, `where`, `set`, and `trim` renderers now use scoped semantic locals, so the generated methods remain readable without numbered mechanical names or additional helper extraction.
- `CompileException` carries the originating `ExecutableElement`; javac errors, including XML parsing failures, now underline the matching Mapper method instead of the interface declaration. External XML resources are not language-model elements, so the diagnostic message retains the XML statement/tag context while navigation targets the Mapper method.
- Verified `mvn -pl lite-orm-core -am verify` including the external Maven Invoker fixture, then verified `mvn clean test` across Core, Spring Boot Starter, and examples.

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

- [x] Document `SqlExecutor`, `Transaction`, `TransactionFactory`, simple callback transactions, Spring transaction participation, and resource ownership.
- [x] Document single-DataSource standalone and Spring examples.
- [x] Document application-level multi-DataSource construction through disjoint Mapper package bindings, transaction-manager matching, routing DataSource ownership, and the absence of same-Mapper multi-binding or core distributed transactions.
- [x] Remove all examples using `SqlEngine`, processors, `ConnectionProvider`, `TransactionCoordinator`, `StandaloneSqlEngine`, or legacy tasks.
- [x] Document extension guidance: typed provider/binder/row mapper/interceptor first; decorator-based routing only for exceptional dynamic routing.
- [x] Run documentation link searches and example builds.
- [x] Commit with `docs: describe sql executor runtime architecture`.
- [x] Push immediately.

Completion notes:
- Rewrote the English overview and synchronized the Chinese standalone, Spring transaction-domain, routing DataSource, and current-roadmap sections with the implemented API.
- Replaced the historical architecture review with the current `Generated Mapper -> SqlExecutor -> TransactionFactory -> Transaction -> JDBC` flow and explicit standalone/Spring ownership rules.
- Added an executable H2 standalone example proving generated Mapper execution, callback commit, and callback rollback through `JdbcAssembly`.
- Verified local Markdown links, searched user documentation and examples for obsolete runtime API examples, ran `mvn -pl lite-orm-examples/basic-mapper -am clean test`, and ran `mvn clean test` for the full reactor.

**Completion criteria:** User documentation matches actual public APIs and clearly explains transaction and multi-DataSource behavior.

### Module R9.2: Perform the Final Architecture Review

**Files:**
- Replace: `docs/architecture/liteorm-architecture-review.md`
- Update this plan's checkboxes and completion notes.

- [x] Review compile-time input, model, generation, runtime execution, standalone assembly, Spring assembly, and multi-DataSource assembly end to end.
- [x] Review SRP, OCP, LSP, ISP, and DIP with concrete class dependencies.
- [x] Review justified patterns only: Strategy for transaction implementations, Factory for transaction creation, Template/explicit lifecycle inside the executor, Interceptor for observation, Adapter for Spring JDBC semantics, Builder only for assembly, Decorator only for optional routing.
- [x] Explicitly reject responsibility-chain usage for fixed JDBC phases.
- [x] Review public API size, package naming, exception taxonomy, concurrency guarantees, test readability, and generated-source readability.
- [x] Record remaining optimization work separately; do not mix benchmarks or caches into correctness changes.
- [x] Run `mvn clean test` and all external examples.
- [x] Commit with `docs: complete runtime architecture review`.
- [x] Push immediately.

Completion notes:
- Accepted the compile-time-to-JDBC dependency direction and the explicit fixed lifecycle; rejected responsibility-chain treatment of JDBC ownership phases.
- Confirmed Strategy, Factory, explicit lifecycle, Interceptor, Spring Adapter, assembly Builder, and optional routing Decorator as the only currently justified patterns.
- Recorded separate API-hardening work for `Transaction` completion semantics, concrete domain-guard exposure, exception hierarchy/redaction, `SqlResult` ownership, implementation-type visibility, and narrative print tests.
- Kept optimization behind a benchmark gate covering generated mapping, dynamic SQL, batch, interceptors, standalone transactions, and Spring participation.

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

- [x] Generated Mapper hot paths use no runtime proxy, reflective method dispatch, runtime XML parsing, or runtime expression engine.
- [x] XML-over-annotation precedence remains method-scoped and emits a compiler warning.
- [x] `SqlExecutor` is the only generated Mapper-facing runtime execution contract.
- [x] `Transaction` is the single connection/commit/rollback/close abstraction.
- [ ] Standalone and Spring transaction implementations pass the same lifecycle characterization suite.
- [x] Multiple application DataSources are isolated through disjoint Mapper groups and explicit executor graphs; one Mapper interface never spans multiple DataSource domains.
- [x] Each Mapper package and Mapper interface binds to exactly one named physical or routing Spring DataSource bean.
- [x] Generated Mapper implementations contain no Spring component, injection, qualifier, or conditional annotations.
- [x] Method-level static DataSource binding is implemented only if a concrete requirement remains after first-stage integration.
- [x] Dynamic read/write, tenant, and shard routing remains owned by the configured DataSource implementation.
- [x] Cross-DataSource calls cannot silently escape an active local or Spring transaction boundary.
- [x] Fixed JDBC phases are not configurable processors.
- [x] Runtime and compiler singletons are concurrency-safe.
- [x] Obsolete APIs, tests, templates, service placeholders, and docs are removed; active template/service assets remain covered by the external Maven fixture.
- [x] `mvn clean test` and external Mapper examples pass from a clean checkout.
- [x] Every completed module has an English commit and has been pushed.
