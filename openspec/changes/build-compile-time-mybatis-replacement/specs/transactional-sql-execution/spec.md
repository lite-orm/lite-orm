## ADDED Requirements

### Requirement: Execute generated mapper calls within a transaction-aware SQL pipeline
Generated mapper implementations SHALL execute statements through a LiteORM SQL pipeline that is aware of the active transaction scope and statement type.

#### Scenario: Execute mapper method without explicit transaction
- **WHEN** application code calls a generated mapper method outside an active transaction
- **THEN** LiteORM executes the statement through the SQL pipeline using non-transactional connection handling rules

### Requirement: Support local transaction scopes for generated mappers
LiteORM SHALL provide a local transaction manager that generated mappers and application code can use to begin, commit, and roll back transaction scopes around multiple mapper calls.

#### Scenario: Commit local transaction across multiple mapper calls
- **WHEN** application code opens a LiteORM-managed transaction and performs multiple generated mapper operations
- **THEN** those operations share one transaction scope
- **THEN** committing the transaction persists all successful changes atomically

### Requirement: Participate in managed transactions when integrated with Spring
When LiteORM is used through the Spring Boot starter and a Spring-managed transaction is active, generated mapper calls SHALL join that transaction scope instead of creating an independent local transaction.

#### Scenario: Join active Spring transaction
- **WHEN** a generated mapper method is invoked inside a Spring `@Transactional` boundary
- **THEN** LiteORM uses the managed transaction context provided by Spring integration
- **THEN** LiteORM does not begin a conflicting local transaction for that mapper call

### Requirement: Define nested transaction behavior explicitly
LiteORM SHALL define one explicit nested transaction policy for this change and apply it consistently across runtime APIs and integration tests.

#### Scenario: Nested local transaction attempt
- **WHEN** application code attempts to begin a second local LiteORM transaction while one is already active on the same execution context
- **THEN** LiteORM applies the documented nested transaction policy consistently
- **THEN** the outcome is observable through the transaction API and covered by tests
