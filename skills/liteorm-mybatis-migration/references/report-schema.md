# Migration Report Schema

The report is evidence for human review. It is not a compatibility matrix and
must link to the versioned LiteORM matrix used for each classification.

## Required Sections

### Scope

Record the target path, revision, authorization boundary, MyBatis version,
LiteORM contract revision or checkout, date, and whether the report is
inspection-only or includes an authorized diff.

### Inventory

List Mapper interfaces, Mapper XML, annotations, Spring wiring, build
dependencies, configuration, tests, and the command used to collect them.
Every non-trivial finding needs a file path and line number.

### Worklist

After inventory, record the bounded migration TODO list. Include one row per
Mapper or configuration boundary with its status (`ready`,
`needs-user-decision`, `blocked`, or `done`), dependencies, source locations,
planned action, and verification command. Update the list after each batch and
identify the next batch explicitly.

### Interaction Log

Record questions asked at authorization or decision gates, the user's decision,
the affected worklist items, and any resulting scope change. If no interaction
was required, write `None`.

### Decisions

Use one row per construct or Mapper method:

| Location | Construct | Matrix status | LiteORM action | Evidence |
| --- | --- | --- | --- | --- |

The `Matrix status` value must be copied from the authoritative matrix at the
pinned revision. The `Evidence` cell links to a contract section or compiler
diagnostic; it must not be replaced by a general claim.

### Converted Scope

Describe each changed file and preserve the semantic invariants that were
checked: SQL, parameters, result shape, statement identity, transaction
ownership, and DataSource binding. Include the review command and a concise
diff summary.

### Interventions

List every manual decision, unsupported construct, unresolved ambiguity, and
requested follow-up. Include the reason automatic conversion stopped and the
owner or input needed to continue. Empty sections must say `None`.

### Verification

Record each exact command, working directory, exit status, and relevant result.
Include compiler diagnostics, generated-source checks, target tests, and
database or external-consumer checks when the migrated behavior requires them.
Distinguish a skipped check from a passing check.

### Claim Boundary

State precisely which inspected constructs were validated and which were not.
Do not generalize from one project to broad MyBatis compatibility. Record
remaining risks, untested paths, and the next review action.
