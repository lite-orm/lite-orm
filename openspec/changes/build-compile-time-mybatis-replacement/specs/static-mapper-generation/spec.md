## ADDED Requirements

### Requirement: Generate static mapper implementations
The compiler SHALL generate a concrete implementation for each supported mapper interface. The generated implementation MUST resolve SQL source, parameter binding, statement metadata, and result mapping without runtime reflection on the execution path.

#### Scenario: Generate mapper implementation from supported interface
- **WHEN** a project compiles a mapper interface with supported LiteORM or MyBatis-style metadata
- **THEN** the processor generates a concrete implementation class for that interface
- **THEN** the generated class invokes LiteORM runtime contracts through precomputed SQL, parameter, and result metadata

### Requirement: Generate deterministic helper code for mapper methods
The compiler SHALL generate stable helper code for each mapper method, including SQL renderer logic, parameter binding order, and result conversion rules, so identical inputs produce identical generated source structure.

#### Scenario: Recompile unchanged mapper source
- **WHEN** the same mapper source and XML definitions are compiled without semantic changes
- **THEN** the generated helper structure remains stable apart from non-semantic content such as timestamps

### Requirement: Fail compilation on unsupported mapper contracts
The compiler SHALL reject mapper methods or mappings that cannot be generated without runtime reflection or unsupported fallbacks. The error MUST identify the offending mapper method or XML node.

#### Scenario: Unsupported mapper signature
- **WHEN** a mapper method uses a contract that LiteORM does not support for static generation
- **THEN** compilation fails
- **THEN** the error message identifies the mapper method and explains why code generation cannot proceed
