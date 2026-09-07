# LiteORM Documentation

This is the canonical documentation index. User-facing guides are grouped by product area under `docs/user/`; normative contracts, project records, and contributor material remain separate so readers can distinguish guidance from guarantees and internal workflow.

## User Documentation

### Start Here

- [`user/README.md`](user/README.md): user documentation map.
- [`user/getting-started.md`](user/getting-started.md): installation, annotation processing, the first Mapper, and runtime assembly.
- [`user/architecture.md`](user/architecture.md): compile-time and runtime architecture.

### Core

- [`user/core/README.md`](user/core/README.md): Core guide index.
- [`user/core/mapping.md`](user/core/mapping.md): JDBC type mappings, generated result mapping, parameter binders, and row mappers.
- [`user/core/extensions.md`](user/core/extensions.md): decision guide for typed extension points and raw JDBC.
- [`user/core/standalone.md`](user/core/standalone.md): standalone JDBC assembly and callback transactions.

### Integrations and Database Types

- [`user/spring/README.md`](user/spring/README.md): Spring guide index.
- [`user/spring/spring-boot.md`](user/spring/spring-boot.md): Mapper registration, named DataSource binding, and Spring transactions.
- [`user/database-types/README.md`](user/database-types/README.md): database type-module index.
- [`user/database-types/postgresql.md`](user/database-types/postgresql.md): PostgreSQL mapping artifact installation and behavior.
- [`user/database-types/mysql.md`](user/database-types/mysql.md): MySQL mapping artifact installation and behavior.

### Migration

- [`user/migration/README.md`](user/migration/README.md): migration guide index.
- [`user/migration/from-mybatis.md`](user/migration/from-mybatis.md): manual migration from supported MyBatis patterns.

## Reference

- [`reference/core-contract.md`](reference/core-contract.md): supported Mapper, JDBC, failure, transaction, concurrency, and database-mapping behavior.
- [`reference/extensions.md`](reference/extensions.md): exact Spring and typed extension contracts.
- [`reference/mybatis-compatibility.md`](reference/mybatis-compatibility.md): supported, partial, and unsupported MyBatis behavior.
- [`../Design-Philosophy.md`](../Design-Philosophy.md): durable product principles and non-goals.
- [`../CONTEXT.md`](../CONTEXT.md): canonical project terminology and ownership.

## Architecture Decisions and Evidence

- [`adr/0001-separate-execution-outcome-from-transaction-completion.md`](adr/0001-separate-execution-outcome-from-transaction-completion.md)
- [`adr/0002-select-jdbc-type-mappings-per-mapper-package.md`](adr/0002-select-jdbc-type-mappings-per-mapper-package.md)
- [`benchmarks/core-ga-baseline.md`](benchmarks/core-ga-baseline.md): reproducible Core GA benchmark evidence.

## Project

- [`project/roadmap.md`](project/roadmap.md): strategic delivery order. GitHub Issues own specifications and implementation status.

## Contributor and Agent Documentation

- [`agent/project-context.md`](agent/project-context.md): repository context and module responsibilities.
- [`agent/engineering-guide.md`](agent/engineering-guide.md): engineering, testing, documentation, and commit conventions.
- [`agent/documentation-policy.md`](agent/documentation-policy.md): document types, ownership, and verification.
- [`agent/tooling.md`](agent/tooling.md): cross-agent workflow and rule discovery.
- [`agents/issue-tracker.md`](agents/issue-tracker.md): GitHub Issues conventions.
- [`agents/triage-labels.md`](agents/triage-labels.md): triage roles and labels.
- [`agents/domain.md`](agents/domain.md): domain-document workflow.

## Documentation Rules

- Root `README.md` and `README_cn.md` are short project entry points; only the Chinese root README is localized for now.
- User guides explain tasks and defer to reference contracts for normative behavior.
- Reference documents own stable guarantees and compatibility classifications.
- ADRs explain durable decisions; benchmarks record reproducible evidence.
- Contributor and agent documents describe repository workflow, not user-facing product behavior.
- Active specifications and delivery state belong in GitHub Issues.
- Every active document must be linked from this index or from a module README.
