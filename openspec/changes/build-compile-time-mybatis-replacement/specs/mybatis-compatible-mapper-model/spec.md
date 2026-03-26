## ADDED Requirements

### Requirement: Support MyBatis-style mapper authoring inputs
LiteORM SHALL allow mapper definitions authored in common MyBatis styles, including mapper interfaces paired with XML statements and mapper interfaces using supported SQL annotations.

#### Scenario: XML-backed mapper interface
- **WHEN** a mapper interface method matches a supported statement defined in its XML mapper resource
- **THEN** LiteORM binds that method to the XML statement during compilation
- **THEN** the generated implementation uses the compiled statement instead of loading XML at runtime

### Requirement: Preserve MyBatis-style parameter naming semantics
LiteORM SHALL resolve mapper method parameters using MyBatis-compatible naming rules for supported cases, including explicit `@Param` names and deterministic fallback names for unannotated parameters.

#### Scenario: Bind named parameters from mapper method
- **WHEN** a mapper method declares multiple supported parameters with and without `@Param`
- **THEN** LiteORM resolves parameter references in XML or annotations using the documented naming rules
- **THEN** generated SQL binding uses those resolved names consistently

### Requirement: Provide migration diagnostics for unsupported compatibility gaps
LiteORM SHALL surface compile-time diagnostics when a MyBatis mapper relies on behavior that is outside the supported compatibility subset for this change.

#### Scenario: Existing MyBatis mapper uses unsupported behavior
- **WHEN** a project compiles an existing MyBatis mapper that depends on unsupported LiteORM compatibility behavior
- **THEN** compilation fails with a compatibility diagnostic
- **THEN** the diagnostic explains the unsupported behavior and points to the mapper method or XML element that must change
