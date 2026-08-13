# LiteORM Incremental Implementation Plan

> **Migration notice (2026-08-13):** This plan records the completed compile-time Mapper MVP work. All unfinished correctness, transaction, concurrency, feature-completion, architecture-review, optimization, and release tasks moved to `docs/plans/liteorm-completion-correctness-architecture-plan.md`. Do not add new implementation tasks here.

> **Status update (2026-08-13):** Modules M0-M9 are complete. The unfinished M10 benchmark/release work and every backlog item have been migrated to `docs/plans/liteorm-completion-correctness-architecture-plan.md`. Continue implementation from the new plan; this file remains the historical record for the compile-time Mapper MVP.

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn the current compile-time Mapper prototype into a reliable MVP that covers the majority of MyBatis-style Mapper use cases through generated static code, while exposing explicit compile-time-bound extension points for exceptional cases.

**Architecture:** Keep the current compile-time-first architecture and treat generated Mapper implementations as the default path. Add three independent escape hatches—SQL providers, custom parameter/result mapping, and execution interceptors—but bind them into generated code at compile time instead of introducing dynamic Mapper proxies, runtime XML interpretation, or implicit reflection fallback. Do not add platform features such as cache, sharding, or pagination until the Mapper MVP and extension contracts are stable.

**Tech Stack:** Java 21, Maven, JUnit 5, annotation processing, FreeMarker, JDBC, Spring Boot 3.1.x, optional H2/Testcontainers for integration verification.

---

## Operating Rules

- Keep every module independently reviewable and testable.
- Mark each checkbox as work progresses.
- Prefer compile-time diagnostics over runtime fallback.
- Preserve the core goal: compile-time MyBatis-style Mapper subset, not full MyBatis compatibility.
- Keep the static generated path as the default; extension points require explicit declaration.
- Bind extension implementations at compile time and generate ordinary Java method calls.
- Keep all public annotations in the LiteORM-owned `org.liteorm.annotation` namespace; do not publish compatibility classes under MyBatis/iBatis package names.
- Never silently fall back to reflection, runtime XML parsing, arbitrary OGNL, or string-based Mapper dispatch.
- Prefer mature, widely used, lightweight libraries when they reduce protocol or infrastructure risk. Do not use OGNL, MVEL, SpEL, or another expression engine: translate the supported OGNL-like subset directly into native Java during annotation processing, and fail compilation for unsupported expressions.
- Keep custom SQL creation, parameter/result conversion, and execution interception as separate contracts.
- Run `mvn clean test` before marking a module complete.
- Do not implement P2 platform features until P0 and P1, including the extension contracts, are stable.

## Static Path and Escape-Hatch Contract

```text
Default path
Mapper interface + XML/annotations
        -> annotation processor
        -> generated MapperImpl
        -> generated SQL renderer / parameter binder / row mapper
        -> SqlEngine / JDBC

Exceptional path, selected explicitly per method or type
generated MapperImpl
        +-> compile-time-bound SqlProvider
        +-> compile-time-bound ParameterBinder / RowMapper
        +-> configured ExecutionInterceptor chain
```

- The default path receives the strongest guarantees: compile-time validation, deterministic generated code, and no runtime Mapper reflection.
- SQL providers may construct SQL at runtime, but their classes and invocation signatures must be validated and called directly by generated code.
- Custom binders and row mappers may handle vendor-specific or complex types, but must use typed interfaces rather than reflective property discovery.
- Execution interceptors may observe or influence defined execution phases, but must not reflectively replace arbitrary internal objects.
- Raw JDBC remains the final escape hatch when a requirement does not fit these contracts; using it explicitly exits LiteORM's compile-time SQL and mapping guarantees for that method.

## Module Map

| Module | Scope | Primary Files |
| --- | --- | --- |
| M0 | Baseline and public contract | `README.md`, `README_cn.md`, `Design Philosophy.md`, `docs/plans/liteorm-incremental-implementation-plan.md` |
| M1 | External project E2E | new demo/fixture module or `lite-orm-core/src/test` fixture |
| M2 | Compile-time diagnostics | `LiteOrmProcessor`, `CompilePipeline`, parsers, parameter parser, diagnostics tests |
| M3 | Dynamic SQL safety | `FreemarkerCodeGenerator`, `XmlBasedSqlParser`, dynamic SQL tests |
| M4 | Result mapping MVP | `CompilePipeline`, mapping model, code generator, mapping tests |
| M5 | Spring Boot usability | spring starter auto-config, properties, transaction processor, integration tests |
| M6 | Runtime SQL provider extension | provider API, annotations, compiler validation, generated invocation tests |
| M7 | Custom binder and row mapper extension | typed SPI, compiler binding, JDBC integration tests |
| M8 | Execution interceptor extension | lifecycle SPI, engine integration, ordering and failure tests |
| M9 | MyBatis migration fixtures | docs, representative mappers, compatibility tests |
| M10 | Benchmark and release readiness | benchmark module or docs, CI docs, release checklist |

## Milestone P0: Make The Current MVP Trustworthy

### Module M0: Documentation and Scope Contract

**Purpose:** Make the project target, current status, compatibility boundary, and next plan explicit.

**Files:**
- Modify: `README.md`
- Modify: `README_cn.md`
- Modify: `Design Philosophy.md`
- Create/modify: `docs/plans/liteorm-incremental-implementation-plan.md`

- [x] Rewrite the Chinese README around the compile-time Mapper platform positioning.
- [x] Add an English README summary and documentation index.
- [x] Update design philosophy to avoid overstating current performance or compatibility.
- [x] Add this incremental implementation plan with module-level checkboxes.
- [x] Run `mvn clean test` (2026-08-12: core 84 tests and spring starter 2 tests passed).
- [x] Review and revise remaining performance or compatibility claims in `Design Philosophy.md` that still read as completed guarantees rather than goals.

**Completion criteria:**
- Documentation clearly separates current verified capability from future platform direction.
- The plan can be executed one module at a time.

### Module M1: External Project End-to-End Verification

**Purpose:** Prove `lite-orm` works outside its own module tests.

**Recommended files:**
- Create: `lite-orm-examples/basic-mapper/pom.xml`
- Create: `lite-orm-examples/basic-mapper/src/main/java/org/liteorm/example/User.java`
- Create: `lite-orm-examples/basic-mapper/src/main/java/org/liteorm/example/UserMapper.java`
- Create: `lite-orm-examples/basic-mapper/src/main/java/org/liteorm/example/UserXmlMapper.java`
- Create: `lite-orm-examples/basic-mapper/src/main/resources/org/liteorm/example/UserXmlMapper.xml`
- Create: `lite-orm-examples/basic-mapper/src/test/java/org/liteorm/example/UserMapperE2ETest.java`
- Modify: root `pom.xml` to include the example only if it should be part of the reactor.

- [x] Add a minimal external-style Maven example that depends on `lite-orm-core`.
- [x] Configure annotation processing explicitly for the example.
- [x] Use H2 or another embedded database for a real table, insert, select, update, delete flow.
- [x] Verify annotation-backed mapper generation.
- [x] Verify XML-backed mapper generation from classpath resources.
- [x] Assert actual database results, not generated-code existence only.
- [x] Run `mvn clean test` (2026-08-12: core 85 tests, spring starter 2 tests, external example 1 test passed).

**Completion criteria:**
- A user can inspect one example and understand how to adopt LiteORM.
- The example proves generated Mapper code compiles and executes against a real database.

### Module M2: Compile-Time Diagnostics Hardening

**Purpose:** Unsupported Mapper contracts fail early with actionable messages.

**Primary files:**
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/LiteOrmProcessor.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/CompilePipeline.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/SqlParameterParser.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/XmlBasedSqlParser.java`
- Add tests under: `lite-orm-core/src/test/java/org/liteorm/test`

- [x] Add tests for unsupported default mapper methods.
- [x] Add tests for unsupported static mapper methods.
- [x] Add tests for unsupported varargs mapper methods.
- [x] Add tests for unknown SQL parameter root.
- [x] Add tests for ambiguous parameter aliases.
- [x] Add tests for missing XML statement referenced by mapper method.
- [x] Add tests for unsupported XML tags.
- [x] Define source precedence: when XML and a SQL annotation both exist for one method, compile the XML statement and emit a method-scoped compiler warning.
- [x] Make compiler errors include mapper interface, method name, and XML node/tag when available.
- [x] Run focused compiler/diagnostics tests (8 tests passed).
- [x] Run `mvn clean test` (2026-08-12: full four-module reactor passed).

**Completion criteria:**
- Unsupported behavior does not silently skip generation.
- Diagnostic messages tell the user what to change.

### Module M3: Dynamic SQL Safety Boundary

**Purpose:** Keep dynamic SQL useful while making unsafe or unsupported constructs explicit.

**Primary files:**
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/FreemarkerCodeGenerator.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/XmlBasedSqlParser.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/AstNode.java` only if new AST metadata is required.
- Add tests under: `lite-orm-core/src/test/java/org/liteorm/test`

- [x] Define the supported expression subset for `test` and `bind`: null/boolean/string/number comparisons, `and`/`or`, simple property paths, array `length`, collection `size()`, and string concatenation in `bind`; reject arbitrary method and static OGNL calls.
- [x] Translate the supported subset directly into native Java source without an expression-engine dependency at compile time or runtime.
- [x] Add tests for supported null checks, boolean operators, empty-string checks, simple property paths, array length, collection size, and bind concatenation through existing generated Mapper fixtures.
- [x] Assert generated source contains native Java operations and no OGNL, MVEL, or SpEL references.
- [x] Add tests for unsupported method calls or complex OGNL expressions.
- [x] Reject `${}` by default; route genuinely dynamic identifiers or SQL structure through the explicit SQL provider extension in M6.
- [x] Add compile-time diagnostics for unsafe `${}` usage.
- [x] Add tests for `foreach` empty collection behavior and ordered binding through the external H2 example.
- [x] Add real H2 tests for dynamic `where`/`foreach` and `set` parameter order; existing generated fixtures continue to cover nested `trim`.
- [x] Run focused dynamic SQL and H2 example tests.
- [x] Run `mvn clean test` (2026-08-12: focused diagnostics, external H2 tests, and full reactor passed).

**Completion criteria:**
- Dynamic SQL behavior is predictable and documented.
- Dangerous string substitution cannot slip into generated SQL unnoticed.

## Milestone P1: Make The MVP Useful For Real Migration

### Module M4: Result Mapping MVP

**Purpose:** Support the common return shapes needed by real Mapper migrations.

**Primary files:**
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/MapperCompilationModel.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/CompilePipeline.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/FreemarkerCodeGenerator.java`
- Add mapping tests under: `lite-orm-core/src/test/java/org/liteorm/test`

- [x] Support record class constructor mapping by selected column order.
- [x] Support JavaBean no-arg constructor plus setter mapping.
- [x] Support simple scalar return types such as `Long`, `Integer`, `String`, and primitive equivalents.
- [x] Support `List<T>` for records, JavaBeans, and scalars.
- [x] Define behavior for empty result: `null` for single object, empty list for `List<T>`.
- [x] Add compile-time failure for unsupported nested object mapping.
- [x] Add tests that assert generated code compiles and maps real rows correctly.
- [x] Run focused result mapping tests.
- [x] Run `mvn clean test`.

**Completion criteria:**
- Typical query return shapes work without reflection-heavy runtime mapping.
- Unsupported complex mapping is rejected clearly.

### Module M5: Spring Boot Starter Usability

**Purpose:** Make Spring adoption realistic instead of only theoretically integrated.

**Primary files:**
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/LiteOrmAutoConfiguration.java`
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/LiteOrmProperties.java`
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/SpringTransactionProcessor.java`
- Add tests under: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot`

- [x] Define how generated MapperImpl classes become Spring beans.
- [x] Add or document Mapper scanning behavior.
- [x] Verify LiteORM uses the application `DataSource`.
- [x] Verify generated mapper calls join active Spring `@Transactional` boundaries.
- [x] Verify mapper calls outside Spring transactions use the documented non-transactional or local transaction behavior.
- [x] Add properties for enabling/disabling starter behavior if needed.
- [x] Add a minimal Spring integration test.
- [x] Run starter tests.
- [x] Run `mvn clean test`.

**Completion criteria:**
- A Spring Boot user can wire LiteORM without manually constructing generated mappers in application code.

### Module M6: Runtime SQL Provider Extension

**Purpose:** Support exceptional SQL construction requirements without restoring runtime Mapper dispatch, XML interpretation, or reflective provider invocation.

**Recommended files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/api/BoundSql.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/BoundParameter.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/SqlProvider.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/annotation/UseSqlProvider.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/MapperCompilationModel.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/CompilePipeline.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/FreemarkerCodeGenerator.java`
- Add tests under: `lite-orm-core/src/test/java/org/liteorm/test/provider`

- [x] Define `BoundSql` as immutable SQL text plus an ordered parameter list; do not use an unordered parameter map as the primary contract.
- [x] Define a typed `SqlProvider<P>` contract whose implementation class is known at compilation time.
- [x] Define an explicit Mapper annotation for selecting a provider on an exceptional method.
- [x] Reject methods that declare both normal annotation/XML SQL and a SQL provider.
- [x] Validate provider visibility, construction requirements, input type compatibility, and return type during annotation processing.
- [x] Generate direct provider construction or reference and an ordinary Java method call; do not use `Class.forName`, `Method.invoke`, or string method names.
- [x] Pass provider-produced SQL and ordered parameters through the existing `ExecutionPlan` and JDBC execution path.
- [x] Use one final provider instance per generated Mapper implementation, created through a compile-time-validated accessible no-arg constructor.
- [x] Add a runtime query fixture that intentionally exceeds the supported XML expression subset.
- [x] Add compile-time diagnostics for invalid signatures, inaccessible providers, missing no-arg constructors, and conflicting SQL sources.
- [x] Reject null `BoundSql`, blank SQL text, or null ordered parameter lists at runtime with a dedicated configuration/execution exception before JDBC preparation.
- [x] Assert generated provider calls contain no reflection APIs.
- [x] Run focused provider tests.
- [x] Run `mvn clean test`.

**Completion criteria:**
- Exceptional methods can create runtime SQL through a typed, compile-time-bound provider.
- Provider usage does not reintroduce dynamic Mapper proxies or reflective method dispatch.
- Normal Mapper methods remain on the fully generated static SQL path.

### Module M7: Custom Parameter Binder and Row Mapper Extension

**Purpose:** Support vendor-specific JDBC types and complex result construction through explicit typed adapters while preserving static Mapper generation.

**Recommended files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/api/ParameterBinder.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/RowMapper.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/annotation/UseParameterBinder.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/annotation/UseRowMapper.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/MapperCompilationModel.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/CompilePipeline.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/compile/FreemarkerCodeGenerator.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/runtime/ParameterProcessor.java` only if generated binders require a runtime hand-off.
- Modify: `lite-orm-core/src/main/java/org/liteorm/runtime/ResultProcessor.java` only if generated row mappers require a runtime hand-off.
- Add tests under: `lite-orm-core/src/test/java/org/liteorm/test/mapping`

- [x] Define `ParameterBinder<T>` around `PreparedStatement`, parameter index, and typed value.
- [x] Define `RowMapper<T>` around `ResultSet` positioned at one current row.
- [x] Support explicit binder selection for a Mapper parameter or mapped Java type without global reflective type lookup.
- [x] Support explicit row mapper selection for single-result and `List<T>` query methods.
- [x] Validate generic target types, adapter visibility, construction requirements, and Mapper method compatibility at compile time.
- [x] Generate direct binder and row mapper calls in the Mapper implementation or generated execution-plan helpers.
- [x] Define null handling: generated code decides whether to call a binder for null, and row mappers run only after `ResultSet.next()` succeeds.
- [x] Define precedence as explicit method/parameter adapter, then generated built-in mapping, otherwise compile-time failure.
- [x] Add an H2 integration test using a non-default value conversion such as a JSON-like string value object.
- [x] Add a custom row mapping test for a shape intentionally unsupported by built-in mapping.
- [x] Add compile-time diagnostics for incompatible adapter types and ambiguous adapter declarations.
- [x] Assert generated adapter invocation uses direct Java calls without reflective construction or property discovery.
- [x] Run focused binder and row mapper tests.
- [x] Run `mvn clean test`.

**Completion criteria:**
- Special JDBC types and custom object construction are supported without weakening built-in static mapping.
- Adapter selection is explicit, typed, deterministic, and validated during compilation.

### Module M8: Execution Interceptor Extension

**Purpose:** Provide a controlled runtime lifecycle extension point for logging, metrics, auditing, routing, authorization, and similar cross-cutting behavior.

**Recommended files:**
- Create: `lite-orm-core/src/main/java/org/liteorm/api/ExecutionInterceptor.java`
- Create: `lite-orm-core/src/main/java/org/liteorm/api/ExecutionInvocation.java` or a similarly narrow immutable context.
- Modify: `lite-orm-core/src/main/java/org/liteorm/DefaultSqlEngine.java`
- Modify: `lite-orm-core/src/main/java/org/liteorm/ExecutionContext.java`
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/LiteOrmAutoConfiguration.java`
- Modify: `lite-orm-spring-boot-starter/src/main/java/org/liteorm/spring/boot/LiteOrmProperties.java` only if configuration is necessary.
- Add tests under: `lite-orm-core/src/test/java/org/liteorm/test/interceptor`
- Add tests under: `lite-orm-spring-boot-starter/src/test/java/org/liteorm/spring/boot`

- [x] Define stable interception phases around execution instead of allowing interception of arbitrary internal methods.
- [x] Expose statement metadata, final SQL, ordered parameters, timing state, result summary, and failure information through a narrow context.
- [x] Prevent interceptors from replacing generated parameter binders or row mappers through reflection.
- [x] For the MVP, allow observation and explicitly modeled routing metadata but reject arbitrary SQL mutation.
- [x] Define deterministic ordering and unwind completion and failure callbacks in reverse order.
- [x] Define exception semantics so interceptor failures never suppress JDBC rollback or resource cleanup.
- [x] Adapt existing logging, slow-query, and audit processors only where necessary; do not build a full observability pack in this module.
- [x] Allow Spring Boot to collect ordered `ExecutionInterceptor` beans without Mapper-specific runtime scanning.
- [x] Add tests for ordering, success callbacks, failure callbacks, cleanup, and transaction interaction.
- [x] Add tests proving zero interceptors preserve current direct execution behavior.
- [x] Run focused interceptor and starter tests (2026-08-13: core and Spring integration suites passed).
- [x] Run `mvn clean test` (2026-08-13: full reactor passed).

**Completion criteria:**
- Cross-cutting behavior has a stable extension contract without MyBatis-style reflective interception of arbitrary internals.
- Generated Mapper dispatch, parameter binding, and result mapping remain static when no interceptor is configured.

### Module M9: MyBatis Migration Fixtures

**Purpose:** Turn compatibility claims into executable examples.

**Recommended files:**
- Create: `docs/mybatis-compatibility.md`
- Create: `docs/migration-guide.md`
- Create: `docs/extensions.md`
- Add fixtures under: `lite-orm-core/src/test/resources/org/liteorm/test/migration`
- Add tests under: `lite-orm-core/src/test/java/org/liteorm/test`

- [x] Document supported annotation patterns.
- [x] Document supported XML dynamic SQL tags.
- [x] Document unsupported MyBatis features with suggested migration paths.
- [x] Document the decision order: generated built-in path first, typed extension second, explicit raw JDBC last.
- [x] Document SQL provider, custom binder/row mapper, and execution interceptor usage and their reduced compile-time guarantees.
- [x] Add fixture for simple CRUD mapper.
- [x] Add fixture for XML dynamic query mapper.
- [x] Add fixture for `foreach` bulk lookup.
- [x] Add fixture for update with `set`.
- [x] Add fixture that intentionally fails on unsupported complex `resultMap`.
- [x] Add a provider fixture for runtime SQL structure that cannot use `${}`.
- [x] Add a custom binder/row mapper fixture for a special value object.
- [x] Add an interceptor fixture demonstrating ordered logging or auditing without changing Mapper dispatch.
- [x] Run migration fixture tests (2026-08-13: 12 diagnostic compilation tests and 9 H2 example tests passed).
- [x] Run `mvn clean test` (2026-08-13: full reactor passed).

**Completion criteria:**
- Compatibility is proven by fixtures, not just README text.
- Users can estimate migration effort before adopting.

## Milestone P2: Prepare For Platform Evolution

### Module M10: Benchmark and Release Readiness

> Migrated without implementation to Phase P4 of `docs/plans/liteorm-completion-correctness-architecture-plan.md`. Performance work now follows transaction, concurrency, feature-completeness, and architecture-correctness work.

**Purpose:** Establish evidence for performance claims and prepare a stable first release.

**Recommended files:**
- Create: `docs/benchmark-plan.md`
- Create: `docs/release-checklist.md`
- Optional create: `lite-orm-benchmark/pom.xml`

- [ ] Define benchmark scenarios against hand-written JDBC and MyBatis.
- [ ] Measure startup time separately from steady-state query execution.
- [ ] Measure simple select, dynamic select, insert/update, and list mapping.
- [ ] Measure no-extension execution, one interceptor, a custom row mapper, and a runtime SQL provider separately.
- [ ] Record methodology before publishing numbers.
- [ ] Add release checklist: Java version, Maven coordinates, annotation processor setup, supported subset, extension contracts, and known gaps.
- [ ] Confirm no README claims exceed benchmark evidence.
- [ ] Run `mvn clean test`.

**Completion criteria:**
- Performance claims are backed by a reproducible methodology.
- Release readiness is tracked by checklist instead of memory.

## Backlog: Do Not Start Before MVP Stability

> Migrated to the deferred backlog in `docs/plans/liteorm-completion-correctness-architecture-plan.md`.

- [ ] SQL metadata export for IDE/AI/static analysis.
- [ ] Production observability interceptor pack: structured logging, slow SQL, tracing, and metrics.
- [ ] Type-safe query DSL.
- [ ] First-level cache.
- [ ] Second-level cache.
- [ ] Read/write splitting.
- [ ] Sharding strategy hooks.
- [ ] Multi-tenant routing.
- [ ] Complex `resultMap` graph mapping.

These are directionally aligned, but starting them before P0/P1 would dilute the core Mapper MVP.
