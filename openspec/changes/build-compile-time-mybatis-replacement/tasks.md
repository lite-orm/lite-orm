## 1. Compile-time mapper model

- [x] 1.1 Define a normalized mapper compilation model that captures statement metadata, parameter names, result mapping, and transaction hints for both XML and annotation inputs
- [x] 1.2 Refactor `LiteOrmProcessor` and `CompilePipeline` to generate concrete mapper implementations from the normalized model
- [x] 1.3 Add compile-time diagnostics for unsupported mapper signatures, mappings, and compatibility gaps with method or XML location details

## 2. Dynamic SQL compilation

- [x] 2.1 Expand the XML and annotation parsing pipeline to represent supported dynamic SQL tags in a shared AST
- [x] 2.2 Generate dedicated SQL renderer helpers that emit final SQL text plus ordered bound parameters for dynamic statements
- [x] 2.3 Add compile-time validation for unsupported dynamic SQL constructs and ambiguous parameter references

## 3. Runtime execution and transactions

- [x] 3.1 Introduce a generated execution-plan contract that mapper implementations pass into the LiteORM SQL engine
- [x] 3.2 Align `DefaultSqlEngine` and processor interfaces with execution plans, deterministic parameter binding, and generated result mapping hooks
- [x] 3.3 Define and implement the local transaction policy, including nested transaction behavior, commit, rollback, and exception propagation

## 4. Spring and compatibility integration

- [x] 4.1 Add Spring Boot starter integration that detects active Spring-managed transactions and joins them instead of opening local transactions
- [x] 4.2 Support MyBatis-style mapper authoring inputs, including XML-backed methods, supported SQL annotations, and `@Param` naming semantics
- [x] 4.3 Publish the supported MyBatis compatibility subset in code-level diagnostics and starter documentation

## 5. Verification and examples

- [x] 5.1 Add compile-time and runtime tests covering generated mapper implementations, parameter naming, and unsupported-contract diagnostics
- [x] 5.2 Add end-to-end fixtures for nested dynamic SQL, transaction participation, rollback behavior, and Spring `@Transactional` integration
- [x] 5.3 Add migration-focused examples and documentation showing how to move representative MyBatis mappers to LiteORM
