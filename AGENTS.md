# LiteORM Agent Rules

This file is the canonical entry point for every coding agent working in this repository. Tool-specific instruction files may point here, but they must not define competing project rules.

## Required Reading

Read these files before making non-trivial changes:

1. `AGENTS.md`
2. `Design-Philosophy.md`
3. `docs/README.md`
4. `docs/agent/project-context.md`
5. `docs/agent/engineering-guide.md`
6. `docs/agent/tooling.md`

For contract or roadmap work, also read the relevant source of truth listed in `docs/README.md`.

## Project Mission

LiteORM is a lightweight compile-time SQL Mapper for Java. It moves Mapper validation, dynamic SQL compilation, parameter planning, and result-mapping generation to javac while keeping runtime execution explicit and JDBC-based.

## Non-Negotiable Architecture

- Generated Mappers depend on `SqlExecutor`, not on a session, proxy, container, or compiler implementation.
- `JdbcSqlExecutor` owns one fixed JDBC lifecycle.
- Spring owns IoC, transaction participation, and DataSource binding; it must not duplicate JDBC execution.
- One Mapper belongs to one DataSource domain.
- Runtime artifacts must not contain the annotation processor, FreeMarker, or another template engine.
- Mapper XML and annotation scripts are compiled; LiteORM does not interpret XML or OGNL at runtime.
- Do not add `SqlSession`, runtime Mapper proxies, ORM caches, lazy loading, automatic count queries, framework `Page<T>`, or a general SQL-rewrite plugin chain.

## Agent Tooling Policy

- `AGENTS.md` is the only canonical project rule file; tool-specific instruction files must remain thin adapters.
- Agent plugins and skills define workflow, not LiteORM architecture; repository rules always remain authoritative.
- Agent plugins and developer-global configuration must never become build or CI dependencies.

## Working Rules

- Inspect the current implementation and tests before proposing changes.
- Resolve uncertainty from the owning contract, tests, and current implementation before asking or assuming. Surface an assumption when it would change public behavior, compatibility, ownership, or task scope.
- Preserve existing behavior unless the task explicitly changes a contract.
- Use characterization tests before changing poorly understood behavior.
- Prefer the smallest coherent change that fixes the root cause.
- Every changed line must contribute directly to the requested outcome or remove code made obsolete by that same change.
- Apply a bounded Boy Scout Rule: leave touched code clean, but do not rename, reformat, reorganize, or refactor unrelated code.
- Keep public contracts narrow and stable; keep compiler internals package-private.
- Avoid speculative abstractions, compatibility layers, and configuration switches.
- Introduce an abstraction only when it owns a concrete invariant, isolates a volatile responsibility, or serves demonstrated current use; never add one solely for hypothetical reuse.
- Define the observable success criteria and the command or test that proves them before implementing a non-trivial change.
- Do not mix unrelated cleanup into a focused change.
- Never silently swallow compiler, JDBC, cleanup, transaction, or resource failures.
- Do not place Testcontainers or test-support dependencies on a user runtime classpath.

## Language Policy

- Source comments, Javadocs, diagnostics documentation, user documentation, rule files, and commit messages must be written in English.
- Localized files such as `README_cn.md` may use their target language.
- Planning artifacts under `docs/superpowers/plans/` and `docs/superpowers/specs/` may use English or Chinese.
- Existing non-English comments are migration debt. Translate them when the owning file is modified; do not add new non-English comments.

## Testing Policy

- Use JUnit 5 for Java tests.
- For a feature, bug fix, or behavior change, first run a focused test that fails for the expected reason, implement the minimum production change, then refactor only while the relevant tests remain green.
- Start with the narrowest relevant test, then run the owning module, then the reactor when the change crosses module boundaries.
- Use database-free unit tests for focused compiler and lifecycle behavior. Every test that creates schema objects or executes generated Mapper SQL must run against PostgreSQL/MySQL Testcontainers. H2 is benchmark-only.
- A release claim requires successful Maven/Gradle external-consumer checks and zero skipped database jobs in CI.
- Do not claim success without fresh command output.

## Documentation Policy

- Maintain one authoritative owner for each project fact.
- Update documentation in the same change as the contract it describes.
- Delete completed implementation plans and superseded architecture snapshots instead of presenting them as current guidance.
- Keep active roadmap specs and plans under `docs/superpowers/` only while they guide unfinished work.
- Update `docs/README.md` when adding, moving, replacing, or deleting documentation.

## Commit Policy

Every commit must have an English Conventional Commit message:

```text
<type>(optional-scope): imperative summary
```

Allowed types:

```text
feat fix refactor test docs build ci chore perf release revert
```

Requirements:

- Use lowercase type and optional scope.
- Use an imperative, specific summary.
- Keep the subject in printable ASCII English.
- Do not use vague subjects such as `update`, `changes`, `fix stuff`, or `WIP`.
- Explain motivation and compatibility impact in the body for non-trivial changes.
- Keep each commit focused on one coherent change.
- Run `scripts/setup-git-hooks.sh` once per clone to enable local validation.

Examples:

```text
feat(processor): reject overloaded mapper methods
fix(jdbc): preserve cleanup failures as suppressed
docs: refresh design philosophy
test(mysql): cover spring rollback with testcontainers
```

## Handoff Checklist

Before finishing:

- Review the diff for accidental changes.
- Run the relevant tests and documentation checks.
- Confirm runtime dependency boundaries when POMs change.
- Confirm new comments and documentation are in English, except allowed planning/localization files.
- Update the source-of-truth documentation.
- Use a valid commit message if committing.
- Report changed files, verification evidence, and remaining risks.
