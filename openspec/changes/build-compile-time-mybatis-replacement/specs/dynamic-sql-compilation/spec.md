## ADDED Requirements

### Requirement: Compile MyBatis-style dynamic SQL into generated code
The compiler SHALL translate supported MyBatis dynamic SQL constructs into generated Java logic instead of interpreting XML or expression trees at runtime. Supported constructs for this change MUST include `if`, `choose`, `when`, `otherwise`, `trim`, `where`, `set`, `foreach`, `sql`, and `include`.

#### Scenario: Compile nested XML dynamic SQL
- **WHEN** a mapper XML statement contains nested supported dynamic SQL tags
- **THEN** compilation succeeds
- **THEN** the generated renderer produces the same SQL branch structure and parameter order defined by the XML semantics

### Requirement: Render final SQL and bound parameters deterministically
Generated dynamic SQL renderers SHALL return final SQL text together with the ordered parameter values required by the statement execution pipeline.

#### Scenario: Render SQL for conditional mapper invocation
- **WHEN** application code invokes a generated mapper method with inputs that activate a subset of dynamic branches
- **THEN** the renderer emits only the SQL fragments enabled by those inputs
- **THEN** the renderer returns parameters in the exact order expected by the final SQL placeholders

### Requirement: Reject unsupported dynamic SQL constructs at compile time
The compiler SHALL fail the build when mapper XML or annotations use dynamic SQL features outside the supported subset for this change.

#### Scenario: Unsupported XML construct encountered
- **WHEN** the parser encounters a dynamic SQL construct that has no compile-time translation in LiteORM
- **THEN** compilation fails before generated sources are emitted
- **THEN** the diagnostic identifies the unsupported construct and its location
