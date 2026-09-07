# Choosing an Extension

Start with generated annotation or XML SQL. Add the narrowest typed extension that owns the exceptional behavior instead of introducing a runtime registry or replacing the JDBC lifecycle.

| Need | Extension | Scope |
| --- | --- | --- |
| Reusable Java/JDBC value representation | `JdbcTypeMappings` and `TypeHandler<T>` | Mapper package, reads and writes |
| Exceptional binding for one parameter | `@UseParameterBinder` and `ParameterBinder<T>` | One Mapper parameter, writes only |
| Runtime SQL structure | `@UseSqlProvider` and `SqlProvider<P>` | One Mapper method |
| Unsupported result-row shape | `@UseRowMapper` and `RowMapper<T>` | One query method, reads only |
| Logs, metrics, audit, or authorization observation | `ExecutionInterceptor` | Executor assembly |
| Exceptional whole-execution routing | `SqlExecutor` decorator | Explicit application assembly |
| Behavior outside generated or typed contracts | Raw JDBC | Application-owned |

JDBC type mappings, parameter binders, providers, and row mappers are validated during compilation and directly referenced by generated code. Runtime type routing uses only the generated immutable routes; it performs no scanning or reflective discovery. Runtime execution still belongs to `JdbcSqlExecutor`.

Use [Choosing a Value or Row Mapping](mapping.md) for the detailed mapping decision. Exact visibility, constructor, lifecycle, thread-safety, null-handling, and host-integration rules are defined in the [Extension contracts](../../reference/extensions.md).
