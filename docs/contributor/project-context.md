# Kervix Project Context

## Repository Purpose

Kervix is a Java 21 compile-time SQL Mapper. It generates ordinary Mapper implementations during annotation processing and executes immutable plans through a fixed JDBC runtime.

## Current Modules

### `kervix-core`

Runtime responsibilities:

- public annotations and extension contracts;
- immutable execution plans and results;
- `JdbcSqlExecutor`;
- standalone transaction support;
- built-in execution interceptors;
- fixed standard JDBC routing through `TypeHandlerManager`.

### `kervix-processor`

Owns compiler-only code and dependencies:

- `KervixProcessor` and `CompilePipeline`;
- annotation and XML parsing;
- dynamic SQL AST and validation;
- generated Mapper source.
- structured source modeling and processor-only FreeMarker source rendering.

### `kervix-spring-boot-starter`

Registers generated Mapper implementations, binds Mapper packages to physical DataSources, supplies Spring-aware connection handles, and participates in Spring transactions. It must depend only on runtime core at runtime.

### `kervix-examples/basic-mapper`

Provides executable annotation/XML, mapping, provider, binder, cursor, transaction, generated-key, batch, and pagination examples.

### `kervix-benchmarks`

Compares Direct JDBC, Kervix, and MyBatis under controlled JMH fixtures. Benchmark results are evidence about the measured setup, not general production latency claims.

### `kervix-test-support`

Provides non-published PostgreSQL and MySQL Testcontainers fixtures for database-backed tests. It is consumed only with test scope and must not enter user runtime dependency trees.

## Planned Modules

- `kervix-migration`: MyBatis scanner and deterministic rewriter.
- `kervix-generator`: DB metadata to model/Mapper skeleton generation.

## Execution Path

```text
Mapper source
  -> kervix-processor / KervixProcessor
  -> CompilePipeline
  -> generated MapperImpl
  -> SqlExecutor
  -> JdbcSqlExecutor
  -> ConnectionHandleFactory
  -> JDBC
```

## Source-of-Truth Map

- Product principles: `Design-Philosophy.md`
- Core contract: `docs/reference/core-contract.md`
- Extensions and Spring boundaries: `docs/reference/extensions.md`
- Migration boundary: `docs/reference/mybatis-compatibility.md` and `docs/user/migration/from-mybatis.md`
- Strategic direction: `docs/project/roadmap.md`
- Active specifications and task sequence: GitHub Issues, configured by `docs/contributor/issue-tracker.md`
- Documentation ownership: `docs/contributor/documentation-policy.md`

## Common Commands

```bash
mvn test
mvn -pl kervix-core test
mvn -pl kervix-spring-boot-starter -am test
mvn -pl kervix-examples/basic-mapper -am test
mvn -pl kervix-benchmarks -am test
gradle -p kervix-examples/external-gradle-processor clean build
git diff --check
scripts/verify-database-test-matrix.sh
```

Database compatibility tests use Docker and may skip locally when Docker is unavailable. Release CI must execute them without skips.

## Current Strategic Direction

The active delivery order is:

```text
correctness
  -> MyBatis deterministic JDBC type parity
  -> API freeze
  -> split processor
  -> external Maven/Gradle verification
  -> public preview
  -> migration tooling
  -> optional DB generator
```

Do not implement later roadmap tools against unstable core, XML, or artifact contracts.
