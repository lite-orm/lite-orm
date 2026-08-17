# LiteORM Documentation Policy

## Language

- English is required for source comments, Javadocs, diagnostics documentation, contributor documentation, architecture documents, and rule files.
- Explicit translations such as `README_cn.md` may use their target language.
- Files under `docs/superpowers/plans/` and `docs/superpowers/specs/` may use English or Chinese because they are working planning artifacts.

## Document Types

### Contract

Defines behavior that code, users, or integrations may rely on. A contract must link to executable tests or verification commands.

### Guide

Explains how to use, extend, migrate, benchmark, release, or contribute. A guide must defer to contracts for normative behavior.

### Evidence

Records benchmark or compatibility results with reproducible commands, environment details, and interpretation limits.

### Active Plan

Describes unfinished work. Active plans live under `docs/superpowers/` and are not product contracts.

## Lifecycle Rules

- Add every active document to `docs/README.md` or a module README.
- Use Git history for completed implementation plans; do not keep completed plans in the active documentation tree.
- Delete architecture reviews when they become stale snapshots.
- Move durable decisions from completed plans into `Design-Philosophy.md`, a contract, or an architecture decision record before deletion.
- Do not use words such as `current`, `final`, or `latest` in a document title unless the document is maintained as a living source of truth.
- Include an exact date when a time-sensitive snapshot is necessary.

## Ownership

- `Design-Philosophy.md` owns durable principles and non-goals.
- `docs/core-ga-contract.md` owns runtime and Mapper contracts.
- `docs/extensions.md` owns extension and Spring boundaries.
- `docs/mybatis-compatibility.md` owns compatibility classification.
- `docs/migration-guide.md` owns manual migration guidance.
- `docs/benchmarks/` owns reproducible performance evidence.
- `docs/superpowers/` owns active design and implementation planning.

## Review Checklist

Before merging documentation changes:

```bash
git diff --check
rg -n '[\p{Han}]' docs README.md 'Design-Philosophy.md' \
  --glob '!README_cn.md' \
  --glob '!docs/superpowers/plans/**' \
  --glob '!docs/superpowers/specs/**'
```

Expected: `git diff --check` succeeds and the language scan has no output.

Also verify:

- local Markdown links resolve;
- deleted documents are no longer linked;
- examples and commands use current module and artifact names;
- normative statements have one authoritative owner;
- roadmap statements are clearly identified as planned work.
