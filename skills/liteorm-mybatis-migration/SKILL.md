---
name: liteorm-mybatis-migration
description: Inspect a MyBatis project and produce a reviewable, compatibility-backed LiteORM migration diff and intervention report. Use for MyBatis-to-LiteORM migrations, compatibility audits, or migration planning; stop for unsupported or ambiguous semantics.
---

# LiteORM MyBatis Migration

Use this skill when a user asks to migrate, audit, or plan migration of a MyBatis
project to LiteORM. The output is a reviewable change set and evidence report, not
a MyBatis-compatible runtime or a permanent Java migration module.

## Guardrails

- Establish the target project and the user's authorization before reading or
  modifying files outside the current workspace. Keep all inspection offline unless
  the user explicitly authorizes fetching a dependency or external project.
- Treat the LiteORM checkout's `docs/reference/mybatis-compatibility.md` as the
  compatibility authority. Read it, `docs/reference/core-contract.md`,
  `docs/reference/extensions.md`, and the relevant migration guide before changing
  a construct. Never copy its classification table into this skill.
- Pin the MyBatis version and LiteORM contract revision used for the report. If
  either is unknown, record that uncertainty and do not make a broad compatibility
  claim.
- Preserve the target project's authorization boundary. Do not publish, deploy,
  push, open a pull request, change issue state, or mutate an external system.
- Prefer a small, explicit source change that the LiteORM compiler can validate.
  Use a typed provider, binder, row mapper, interceptor, Spring adapter, or raw
  JDBC only when the authoritative contract assigns that behavior to it.
- Stop and record an intervention when semantics are unsupported, ambiguous, or
  depend on runtime interpretation. In particular, do not guess about OGNL,
  `${...}` substitution, nested result graphs, plugins, caches, sessions, lazy
  loading, dynamic DataSource selection, or custom language drivers.

## Workflow

### 1. Establish scope

Record:

- target project path and revision;
- authorization to edit the target;
- MyBatis version and relevant Spring/integration versions;
- LiteORM checkout or pinned contract source;
- requested output directory and whether source edits are authorized.

When the target is not already present, ask the user to provide it or identify a
legally usable, licensed source. Do not clone or modify an external project on
assumption.

### 2. Inventory the project

Run the deterministic offline inventory helper from the skill directory:

```bash
python3 scripts/scan_mybatis.py \
  --project /path/to/project \
  --output /path/to/report/mybatis-inventory.md
```

The helper only reads source and build files and emits evidence with file and line
locations. It does not edit the project, resolve dependencies, execute SQL, or
declare compatibility. Read the generated inventory and inspect the referenced
Mapper interfaces, XML, Spring wiring, dependency files, and tests.

### 3. Build a migration worklist

After the first inventory, create a reviewable TODO list before making source
changes. Group work by Mapper or configuration boundary, preserve source paths
and line ranges, and order items by dependency: contract classification first,
then deterministic conversion, then compiler/tests. Mark each item `ready`,
`needs-user-decision`, `blocked`, or `done`. A large project is expected to be
processed in bounded batches; finish and report one batch before starting the
next rather than attempting an unbounded all-at-once rewrite.

Keep the worklist in the migration report and update it after every batch. A
worklist item is complete only when its classification, change (if authorized),
and verification result are recorded.

### 4. Classify against the contract

For every Mapper method or configuration construct, consult the compatibility
matrix and record one of its statuses in the report. Keep the evidence local:
source location, relevant declaration, chosen LiteORM contract, and reason.
Separate:

- deterministic conversions that can be compiled and tested;
- typed extension or application-owned replacements;
- explicit rejections;
- ambiguous constructs requiring human decisions.

Do not turn a file-level signal into a method-level conclusion without inspecting
the declaration. A `${...}` signal, for example, requires identifying whether it
is an unsafe SQL substitution, a literal in a comment, or a project-specific
extension.

### 5. Interact at decision gates

Pause and ask the user a concise, specific question when progress depends on
missing authorization, an ambiguous construct, a rejected feature that needs a
replacement, a conflicting DataSource or transaction choice, a compiler/test
failure, or whether to continue with the next worklist batch. Record the question,
the user's decision, and the affected worklist items in the report. Continue
independent, already-authorized items while a decision is pending only when that
cannot change the user's requested scope or semantics.

### 6. Produce the smallest migration diff

With edit authorization, change only constructs classified as deterministic or
covered by an explicit replacement. Preserve SQL meaning, parameter names,
statement identity, result shape, transaction ownership, and DataSource
boundaries. Keep MyBatis XML only where the LiteORM XML subset owns the syntax.

Do not automatically rewrite ambiguous expressions or graph mappings. Add each
manual decision to the intervention report before making a dependent edit. Keep
the target project's normal build files free of this skill and free of
annotation-processor implementation dependencies.

### 7. Use compiler feedback

Run the narrowest target compilation or test that exercises each changed Mapper.
Then run the target module and its relevant integration tests. If the target is
being migrated to a local LiteORM checkout, use the checkout's documented
external-consumer or processor verification command. Treat compiler diagnostics,
test failures, and generated-source differences as migration findings; do not
silence them with runtime reflection or fallback interpretation.

### 8. Deliver evidence

Write a report using [references/report-schema.md](references/report-schema.md).
It must include:

- target and version policy;
- files and constructs inspected;
- converted constructs and the resulting diff;
- unsupported constructs;
- manual interventions and unresolved decisions;
- compiler findings and test results;
- exact verification commands and their exit status;
- remaining risks and the boundary of any compatibility claim.

Show the final diff and verify it is reviewable with `git diff --check` and the
target project's own review/build commands. A clean report with no source change
is a valid result when the project requires only manual intervention.

## Stopping Conditions

Stop source conversion and report the blocker when authorization, contract
evidence, version identity, or required project files are missing; when a
construct is ambiguous or explicitly outside LiteORM's contract; when compiler
diagnostics or tests fail; or when the requested action would publish or mutate
an external system. Resume only after the user supplies the missing decision or
authorization.
