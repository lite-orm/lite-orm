# Lynxus Engineering Guide

## Design Bias

- Optimize for low cognitive load and explicit ownership.
- Prefer deep modules with small semantic interfaces.
- Keep volatile parsing, JDBC, transaction, and generation details behind the module that owns them.
- Do not add wrappers or abstractions that only rename an existing operation.
- Keep uncertain choices reversible and validate them with a thin executable slice.

These rules are adapted for Lynxus from the layered rule-set approach demonstrated by [ciembor/agent-rules-books](https://github.com/ciembor/agent-rules-books); Lynxus remains governed by its own architecture and contracts.

## Java Style

- Target Java 21.
- Use descriptive names and standard Java formatting.
- Prefer immutable values and records where they improve ownership and readability.
- Keep compiler implementation types package-private unless they are the documented processor entry point.
- Avoid reflection in generated Mapper dispatch, parameter planning, and standard result mapping.
- Do not add Lombok or another code-generation dependency without an approved design.
- Do not use one-letter names except conventional loop indices in very small scopes.
- Keep the happy path readable and make failure/cleanup ownership explicit.

## Comments and Javadocs

- Write comments and Javadocs in English.
- Document public contracts, invariants, resource ownership, thread safety, nullability, and non-obvious rationale.
- Do not narrate code that is already clear.
- Do not use comments to compensate for weak names or poor decomposition.
- Translate existing non-English comments when modifying their file.

## Compiler Changes

- Start with a failing compile-testing or javac fixture.
- Attach diagnostics to the most specific Mapper element available.
- Include stable context such as Mapper, method, XML path, namespace, statement, tag, or attribute.
- Never print compiler failures to `System.err` or use `printStackTrace()`.
- Keep parsing secure and offline.
- Generated source must be deterministic and depend only on public runtime APIs.

## JDBC and Transaction Changes

- Characterize acquisition, preparation, binding, execution, result reading, mapping, cleanup, and terminal callbacks.
- Preserve the original failure as primary and attach cleanup failures as suppressed.
- Do not allow interceptor failures to overwrite SQL, mapping, cleanup, or transaction failures.
- Keep standalone and Spring execution on the same `JdbcSqlExecutor` lifecycle.
- Make connection and transaction domain ownership explicit.

## Testing Strategy

Use the smallest test that proves the contract:

1. focused unit or compiler test;
2. owning module test suite;
3. real-driver or Spring integration test when the boundary requires it;
4. full reactor and external consumers for release-facing changes.

Test categories:

- database-free unit tests: compiler, lifecycle, validation, and wiring feedback;
- PostgreSQL/MySQL Testcontainers: every schema-backed Mapper, JDBC driver, transaction, and metadata behavior;
- Spring tests: IoC, physical DataSource binding, transaction participation, and domain mismatch;
- golden source tests: deterministic generated Java;
- Maven Invoker/Gradle fixtures: published annotation-processor consumption;
- JMH: performance only after correctness gates pass.

H2 is reserved for the controlled JMH benchmark fixture and must not be used for functional or integration behavior.

## Documentation Changes

- Write user and contributor documentation in English.
- `README_cn.md` and other explicitly localized files may use their target language.
- Update the owning contract, not several copies of the same fact.
- Delete superseded plans and reviews after their durable conclusions move into a contract or philosophy document.
- Keep roadmap direction in `docs/project/roadmap.md` and delivery state in GitHub Issues.
- Run the link and language checks documented in `docs/contributor/documentation-policy.md`.

## Commit Messages

Use English Conventional Commits:

```text
type(optional-scope): imperative summary
```

Allowed types are `feat`, `fix`, `refactor`, `test`, `docs`, `build`, `ci`, `chore`, `perf`, `release`, and `revert`.

Good examples:

```text
fix(processor): report malformed mapper xml
refactor(core): isolate final execution outcome
docs: remove completed implementation plans
ci: validate conventional commit messages
```

Rejected examples:

```text
update
changes
WIP
fix issue
fix: stuff
```

Run `scripts/setup-git-hooks.sh` once per clone. CI validates all pull-request commit subjects.

## Agent Workflow

- Treat `AGENTS.md` as the canonical project policy across all agent clients.
- Use the Matt Pocock engineering skills as the primary workflow layer for specification, ticketing, TDD, debugging, review, and delivery.
- Publish active specifications and tracer-bullet tickets to GitHub Issues; promote only durable conclusions into contracts, ADRs, or `CONTEXT.md`.
- Follow `docs/contributor/tooling.md` for the shared workflow and thin-adapter expectations.

## Completion Standard

A change is complete only when:

- the requested behavior is implemented;
- relevant tests have fresh passing output;
- dependency boundaries still hold;
- documentation and examples match the implementation;
- comments and documentation follow the language policy;
- the diff contains no unrelated changes;
- the commit message passes repository validation.
