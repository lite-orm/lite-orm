# LiteORM Roadmap

This document owns strategic delivery order. It does not track implementation steps, assignees, dependencies, or completion state. GitHub Issues own active specifications and tracer-bullet tickets; contracts, ADRs, and `CONTEXT.md` own durable facts.

## Completed Foundation

- One final JDBC execution outcome after executor-owned cleanup, separate from transaction completion.
- Deterministic compiler rejection, method-overload validation, and fail-closed offline XML parsing.
- A frozen current JDBC matrix including UUID, LocalTime, and OffsetDateTime.
- PostgreSQL 16.4 and MySQL 8.4 Testcontainers coverage for database-backed Core, Spring, and example behavior.
- H2 isolated to the controlled JMH benchmark fixture.

## Public Release Priorities

### MyBatis JDBC Type Parity

Use MyBatis 3.5.19 deterministic built-in TypeHandlers as the compatibility baseline while preserving compile-time generation. Select one explicit JDBC type-mapping collection per Mapper package, ship independently tested PostgreSQL and MySQL collections, and allow users or third parties to provide additional collections through ordinary dependencies. Complete the missing scalar and temporal types, enum ordinal mapping, national-character and structured JDBC values, and lifecycle-safe LOB handling. Do not add runtime discovery, unknown-object fallback, or a mutable type-handler registry.

### Public API And Artifact Boundaries

Freeze the supported public API with binary compatibility checks. Replace FreeMarker with deterministic JDK-only source generation, then move annotation processing into `lite-orm-processor` so runtime consumers do not carry compiler implementation or template dependencies.

### External Consumption And Spring Support

Verify Maven and Gradle consumers outside the reactor. Keep Spring limited to bean assembly, DataSource binding, and transaction participation, and maintain explicit supported-version gates.

### Release Engineering

Complete artifact metadata, license and repository checks, reproducible release workflows, published documentation, and zero-skip PostgreSQL/MySQL release gates.

## Migration And XML Priorities

- Publish an immutable LiteORM Mapper DTD with offline resolution.
- Complete the controlled XML subset, including integrity diagnostics and explicitly supported statement attributes.
- Maintain executable annotation, XML, standalone, Spring, multi-DataSource, and MyBatis migration examples.
- Build a read-only MyBatis compatibility scanner before any deterministic safe rewriter.
- Validate migration tooling against licensed external projects before claiming broad compatibility.

## Optional Tooling

A DB-to-Mapper generator may ship on an independent version track after Core, processor, XML, and artifact contracts stabilize. New ecosystem features require demonstrated user demand, a named owner, an executable contract, and a design that preserves LiteORM's fixed runtime boundaries.
