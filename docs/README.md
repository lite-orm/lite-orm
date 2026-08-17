# LiteORM Documentation Index

This index identifies the current source of truth for each project concern. Documents not listed here must not be treated as active architecture or product contracts.

## Start Here

- [`../README.md`](../README.md): English project introduction and quick start.
- [`../README_cn.md`](../README_cn.md): Chinese project introduction and quick start.
- [`../Design Philosophy.md`](../Design%20Philosophy.md): durable product and architecture principles.
- [`agent/project-context.md`](agent/project-context.md): fast repository context for contributors and coding agents.
- [`agent/engineering-guide.md`](agent/engineering-guide.md): engineering, testing, documentation, and commit conventions.
- [`agent/tooling.md`](agent/tooling.md): cross-agent rule discovery, Superpowers version locking, and environment verification.

## Current Contracts

- [`core-ga-contract.md`](core-ga-contract.md): supported core behavior, failures, transactions, concurrency, and non-goals.
- [`extensions.md`](extensions.md): Spring binding and typed extension contracts.
- [`mybatis-compatibility.md`](mybatis-compatibility.md): current MyBatis compatibility boundary.
- [`migration-guide.md`](migration-guide.md): manual migration guidance.
- [`benchmarks/core-ga-baseline.md`](benchmarks/core-ga-baseline.md): reproducible benchmark baseline and interpretation limits.

## Active Roadmap

- [`superpowers/specs/2026-08-17-liteorm-adoption-roadmap-design.md`](superpowers/specs/2026-08-17-liteorm-adoption-roadmap-design.md): approved roadmap design.
- [`superpowers/plans/2026-08-17-liteorm-adoption-roadmap.md`](superpowers/plans/2026-08-17-liteorm-adoption-roadmap.md): active implementation plan.

## Documentation Lifecycle

- Contracts describe behavior users and modules may rely on.
- Guides explain how to use or migrate to those contracts.
- Benchmarks record reproducible evidence and its limits.
- Active plans describe unfinished work and may use English or Chinese.
- Completed plans and superseded architecture snapshots are deleted; Git history preserves them.
- Every new document must be linked from this index or from a module README with a clear owner.
