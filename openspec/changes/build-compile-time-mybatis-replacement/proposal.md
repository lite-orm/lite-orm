## Why

LiteORM already targets compile-time SQL and mapping generation, but it still needs a clear product contract for becoming a practical MyBatis replacement. This change defines the capabilities required to remove runtime reflection from the mapper path while preserving the MyBatis features teams depend on, especially dynamic SQL, transactions, and XML or annotation driven mapper authoring.

## What Changes

- Define a compile-time mapper generation model that parses mapper interfaces plus XML or annotations and emits static Java implementations, parameter binders, and result mappers.
- Define how MyBatis-style dynamic SQL is translated into generated code instead of runtime reflection or expression parsing on the hot path.
- Define the runtime execution contract for SQL execution, transaction coordination, exception handling, and extension points used by generated mappers.
- Define the compatibility surface needed for teams to migrate existing MyBatis mapper code incrementally.
- Add implementation tasks covering compiler work, runtime contracts, Spring integration points, tests, samples, and documentation.

## Capabilities

### New Capabilities
- `static-mapper-generation`: Generate mapper implementations, SQL renderers, parameter binders, and result mapping code during compilation with no runtime reflection in the execution path.
- `dynamic-sql-compilation`: Support MyBatis-style dynamic SQL constructs by compiling them into deterministic Java code that produces SQL text and bound parameters.
- `transactional-sql-execution`: Execute generated mapper calls through a transaction-aware SQL pipeline that supports local transactions, nested scopes, and integration hooks for Spring-managed transactions.
- `mybatis-compatible-mapper-model`: Provide a mapper programming model compatible with common MyBatis XML and annotation workflows so existing applications can migrate incrementally.

### Modified Capabilities
- None.

## Impact

- Affects `lite-orm-core` compile pipeline, XML and annotation parsers, code generation templates, runtime execution interfaces, and transaction management.
- Affects `lite-orm-spring-boot-starter` for managed transaction integration and configuration wiring.
- Adds or expands end-to-end tests, migration fixtures, and example mappers covering dynamic SQL and transaction behavior.
- Establishes the baseline contract for future caching, auditing, pagination, and sharding work without making them required in this change.
