# LiteORM Project Context

## Repository Purpose

LiteORM is a Java 21 compile-time SQL Mapper. It generates ordinary Mapper implementations during annotation processing and executes immutable plans through a fixed JDBC runtime.

## Current Modules

### `lite-orm-core`

Currently contains both runtime APIs and the annotation processor. The active roadmap splits compiler code into `lite-orm-processor` and removes FreeMarker before public preview.

Runtime responsibilities:

- public annotations and extension contracts;
- immutable execution plans and results;
- `JdbcSqlExecutor`;
- standalone transaction support;
- built-in execution interceptors.

Current compiler responsibilities:

- `LiteOrmProcessor` and `CompilePipeline`;
- annotation and XML parsing;
- dynamic SQL AST and validation;
- generated Mapper source.

### `lite-orm-spring-boot-starter`

Registers generated Mapper implementations, binds Mapper packages to physical DataSources, supplies Spring-aware connection handles, and participates in Spring transactions. It must depend only on runtime core at runtime.

### `lite-orm-examples/basic-mapper`

Provides executable annotation/XML, mapping, provider, binder, cursor, transaction, generated-key, batch, and pagination examples.

### `lite-orm-benchmarks`

Compares Direct JDBC, LiteORM, and MyBatis under controlled JMH fixtures. Benchmark results are evidence about the measured setup, not general production latency claims.

### `lite-orm-test-support`

Provides non-published PostgreSQL and MySQL Testcontainers fixtures for database-backed tests. It is consumed only with test scope and must not enter user runtime dependency trees.

## Planned Modules

- `lite-orm-processor`: compiler and JDK-only source generation.
- `lite-orm-migration`: MyBatis scanner and deterministic rewriter.
- `lite-orm-generator`: DB metadata to model/Mapper skeleton generation.

## Execution Path

```text
Mapper source
  -> LiteOrmProcessor
  -> CompilePipeline
  -> generated MapperImpl
  -> SqlExecutor
  -> JdbcSqlExecutor
  -> ConnectionHandleFactory
  -> JDBC
```

## Source-of-Truth Map

- Product principles: `Design-Philosophy.md`
- Core contract: `docs/core-ga-contract.md`
- Extensions and Spring boundaries: `docs/extensions.md`
- Migration boundary: `docs/mybatis-compatibility.md` and `docs/migration-guide.md`
- Strategic direction: `docs/roadmap.md`
- Active specifications and task sequence: GitHub Issues, configured by `docs/agents/issue-tracker.md`
- Documentation ownership: `docs/agent/documentation-policy.md`

## Common Commands

```bash
mvn test
mvn -pl lite-orm-core test
mvn -pl lite-orm-spring-boot-starter -am test
mvn -pl lite-orm-examples/basic-mapper -am test
mvn -pl lite-orm-benchmarks -am test
git diff --check
```

Database compatibility tests use Docker and may skip locally when Docker is unavailable. Release CI must execute them without skips.

## Current Strategic Direction

The active delivery order is:

```text
correctness
  -> MyBatis deterministic JDBC type parity
  -> API freeze
  -> replace FreeMarker
  -> split processor
  -> external Maven/Gradle verification
  -> public preview
  -> migration tooling
  -> optional DB generator
```

Do not implement later roadmap tools against unstable core, XML, or artifact contracts.
