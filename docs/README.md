# LiteORM Documentation Index

This index identifies the current source of truth for each project concern. Documents not listed here must not be treated as active architecture or product contracts.

## Start Here

- [`../README.md`](../README.md): English project introduction and quick start.
- [`../README_cn.md`](../README_cn.md): Chinese project introduction and quick start.
- [`../Design-Philosophy.md`](../Design-Philosophy.md): durable product and architecture principles.
- [`../CONTEXT.md`](../CONTEXT.md): canonical project terminology for compilation and execution ownership.
- [`agent/project-context.md`](agent/project-context.md): fast repository context for contributors and coding agents.
- [`agent/engineering-guide.md`](agent/engineering-guide.md): engineering, testing, documentation, and commit conventions.
- [`agent/tooling.md`](agent/tooling.md): cross-agent rule discovery and the shared non-trivial-work workflow.
- [`agents/issue-tracker.md`](agents/issue-tracker.md): GitHub Issues conventions for specifications and implementation tickets.
- [`agents/triage-labels.md`](agents/triage-labels.md): triage role to GitHub label mapping.
- [`agents/domain.md`](agents/domain.md): rules for consuming `CONTEXT.md` and ADRs.

## Current Contracts

- [`core-ga-contract.md`](core-ga-contract.md): supported core behavior, failures, transactions, concurrency, and non-goals.
- [`extensions.md`](extensions.md): Spring binding and typed extension contracts.
- [`mybatis-compatibility.md`](mybatis-compatibility.md): current MyBatis compatibility boundary.
- [`migration-guide.md`](migration-guide.md): manual migration guidance.
- [`benchmarks/core-ga-baseline.md`](benchmarks/core-ga-baseline.md): reproducible benchmark baseline and interpretation limits.

## Architecture Decisions

- [`adr/0001-separate-execution-outcome-from-transaction-completion.md`](adr/0001-separate-execution-outcome-from-transaction-completion.md): explains why execution outcomes include cleanup but exclude transaction completion.
- [`adr/0002-select-jdbc-type-mappings-per-mapper-package.md`](adr/0002-select-jdbc-type-mappings-per-mapper-package.md): explains compile-time database-family mapping selection without a runtime registry.

## Active Roadmap

- [`roadmap.md`](roadmap.md): strategic delivery order and compatibility priorities. GitHub Issues own specifications and implementation status.

## Documentation Lifecycle

- Contracts describe behavior users and modules may rely on.
- Guides explain how to use or migrate to those contracts.
- Benchmarks record reproducible evidence and its limits.
- GitHub Issues describe unfinished specifications, tickets, dependencies, and delivery state.
- Completed plans and superseded architecture snapshots are deleted; Git history preserves them.
- Every new document must be linked from this index or from a module README with a clear owner.
