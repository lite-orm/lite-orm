# Testcontainers Database Test Matrix Design

Date: 2026-08-31

## Status

Approved direction, pending implementation-plan review.

This specification refines the database-testing portions of the adoption roadmap. Where the roadmap still describes H2-backed functional tests or a MySQL-only Testcontainers matrix, this specification is authoritative.

## Goal

Run every database-backed functional, integration, example, and end-to-end contract against real PostgreSQL and MySQL servers while retaining H2 only for the controlled JMH benchmark baseline.

## Decision

LiteORM will use three distinct test categories:

1. Pure unit and wiring tests use no database. They may use narrow JDBC or `DataSource` test doubles when the observable contract is connection participation, lifecycle accounting, bean registration, or validation rather than SQL behavior.
2. Every test that creates schema objects or executes generated Mapper SQL runs against both PostgreSQL 16.4 and MySQL 8.4 through Testcontainers and the real vendor JDBC drivers.
3. The benchmark module continues to use H2 2.3.232 because it measures framework and JDBC-call overhead under a stable in-process fixture. Benchmark results remain explicitly excluded from production-driver compatibility claims.

H2 compatibility modes are not permitted in functional or integration tests.

## Container And Dependency Architecture

A non-published `lite-orm-test-support` module will own the shared PostgreSQL and MySQL fixtures. It will provide:

- fixed default images `postgres:16.4-alpine` and `mysql:8.4.0`;
- real `PGSimpleDataSource` and `MysqlDataSource` instances;
- isolated schema/database allocation and deterministic schema reset;
- SQL resource execution with engine-specific substitutions;
- diagnostic access to the image, JDBC URL, and container logs;
- a small engine enum or contract that lets one abstract JUnit contract run through two concrete subclasses.

The module is reactor-only, has deployment disabled, and is consumed exclusively with Maven `test` scope. Testcontainers, vendor test fixtures, and `lite-orm-test-support` must not appear in any published runtime dependency tree.

Containers should be shared at class or JVM scope where isolation remains deterministic. Each test receives an isolated logical database or schema so parallel and multi-DataSource tests cannot leak rows or transaction state into one another.

## Test Migration

### Core

The existing PostgreSQL/MySQL compatibility contract remains the driver-level type and execution matrix. H2-only database tests are migrated as follows:

- `JdbcTypeRuntimeTest` is removed after its null, scalar, record, and unsupported-conversion assertions are owned by the dual-driver compatibility and converter tests.
- `MultiDataSourceExecutionTest` becomes a shared contract with PostgreSQL and MySQL subclasses. Each run uses two isolated DataSource domains from the same engine.
- `CoreConcurrencySoakTest` becomes a shared PostgreSQL/MySQL contract while retaining connection ownership instrumentation around the real vendor DataSources.

Compiler tests, executor tests with deliberate JDBC proxies, and other tests that do not execute database SQL remain database-free.

### Spring Boot Starter

Spring tests are split by responsibility:

- bean-name, binding-validation, overlap, and missing-bean tests use inert DataSource test doubles and do not start containers;
- generated Mapper execution, package-to-DataSource isolation, routing, concurrent calls, and Spring transaction commit/rollback run through one shared contract on PostgreSQL and MySQL;
- connection-participation tests that intentionally count proxy connection calls remain database-free because a real database would obscure the lifecycle invariant they verify.

The starter removes its H2 dependency and adds only test-scoped access to the shared fixture and both vendor drivers.

### Examples

The basic Mapper example executes its standalone assembly and rollback contract on both PostgreSQL and MySQL. Its H2 dependency is removed. The example README describes the Testcontainers requirement and the two verified engines.

### Benchmarks

`lite-orm-benchmarks` remains unchanged in architecture and keeps H2 as its only database. Benchmark documentation continues to state that the measurements are an in-process overhead baseline, not PostgreSQL or MySQL latency evidence.

## CI And Local Execution

The database workflow exposes explicit PostgreSQL and MySQL jobs or matrix entries and stores their Surefire reports. Database release gates require:

- Docker available;
- every designated database contract executed on both engines;
- zero skipped PostgreSQL/MySQL jobs;
- no H2 dependency outside `lite-orm-benchmarks`;
- no Testcontainers or test-support artifact on user runtime classpaths.

Local focused commands may use Testcontainers' Docker availability handling, but a release or PR completion claim requires fresh output with zero skipped database tests.

## Documentation Changes

Implementation updates the canonical testing rules and user-facing module descriptions:

- `AGENTS.md` and `docs/agent/engineering-guide.md` state that database-backed functional tests use PostgreSQL/MySQL Testcontainers, while H2 is benchmark-only.
- `Design-Philosophy.md`, the root README files, example documentation, core contract, extension contract, and compatibility guide remove H2 functional-test claims.
- the active roadmap is updated so its shared test-support and database-matrix work covers both PostgreSQL and MySQL and no longer schedules H2-backed E2E fixtures.

## Success Criteria

The change is complete when all of the following are true:

- repository search finds H2 production/test usage only in `lite-orm-benchmarks` and benchmark documentation;
- every database-backed Core, Starter, and basic-example test has PostgreSQL and MySQL executions;
- focused PostgreSQL and MySQL suites pass with zero skips;
- the complete Maven reactor passes;
- runtime dependency-tree checks contain neither Testcontainers nor `lite-orm-test-support`;
- documentation checks and `git diff --check` pass.

## Commit And Review Shape

The already completed JDBC type/lifecycle contract remains one focused commit. The Testcontainers support module, dual-driver migration, H2 dependency removal, CI gate, and documentation updates form a second focused test-infrastructure commit. The branch is then pushed and opened as a pull request against its configured base branch.
