# Choosing a Value or Row Mapping

LiteORM has one generated default path and three typed mapping extension points. They operate at different scopes and solve different problems.

The shortest mental model is:

- a **JDBC value adapter** defines how one reusable Java value maps to one JDBC column value;
- **generated result mapping** composes column values into a scalar, record, or JavaBean;
- a **parameter binder** changes how one Mapper parameter is written;
- a **row mapper** changes how one query method constructs an object from a whole row.

All three choices are resolved during compilation. Generated Mappers hold direct references to the selected implementation. LiteORM does not search a runtime registry or choose a mapping from live database metadata.

## Start With the Generated Path

Use ordinary Mapper parameters and scalar, record, or JavaBean results whenever LiteORM already supports the shape. No extension is needed:

```java
@Select("SELECT id, name FROM users WHERE id = #{id}")
User findById(long id);
```

Generated mapping provides the strongest compile-time validation and keeps application code smallest.

## Choose by Scope

| Concept | Scope | Direction | Input/output shape | Use it when |
| --- | --- | --- | --- | --- |
| Generated mapping | Mapper method | Write and read | Supported parameters, scalars, records, and JavaBeans | LiteORM already understands the Java and JDBC representation. |
| `@JdbcTypeMapping` with `JdbcValueAdapter<T>` | Mapper package | Write and read | One Java value and one JDBC column value | A Java type has a reusable database-family representation. |
| `@UseParameterBinder` with `ParameterBinder<T>` | One Mapper parameter | Write only | One annotated parameter | One parameter needs exceptional binding that should not become package policy. |
| `@UseRowMapper` with `RowMapper<T>` | One query method | Read only | One current result row, possibly several columns | One result shape cannot be generated as a scalar, record, or JavaBean. |

```mermaid
flowchart TD
    A[What needs custom mapping?] --> B{Is the generated mapping sufficient?}
    B -->|Yes| G[Use the generated path]
    B -->|No| C{Is this a reusable one-value representation?}
    C -->|Yes, write and read one JDBC value| J[JdbcTypeMapping plus JdbcValueAdapter]
    C -->|No| D{Is only one parameter binding exceptional?}
    D -->|Yes| P[UseParameterBinder plus ParameterBinder]
    D -->|No| E{Does one query need a custom whole-row result?}
    E -->|Yes| R[UseRowMapper plus RowMapper]
    E -->|No| X[Use a SqlProvider or raw JDBC for a different kind of extension]
```

## JDBC Type Mapping: Reusable Value Policy

The three similarly named types form one module; they are not three competing extension choices:

| Name | Role |
| --- | --- |
| `JdbcTypeMappings` | The selected collection for a Mapper package. |
| `@JdbcTypeMapping` | One declarative entry in that collection. |
| `JdbcValueAdapter<T>` | The implementation that writes and reads the declared value representation. |

Each `@JdbcTypeMapping` entry associates:

1. one Java type;
2. one `JDBCType` representation;
3. one `JdbcValueAdapter<T>` implementation.

The collection is selected once for every package that directly contains Mappers. The processor validates it and generates direct adapter calls for matching parameters and supported scalar results.

```java
@JdbcTypeMapping(
    javaType = Money.class,
    jdbcType = JDBCType.DECIMAL,
    adapter = MoneyJdbcValueAdapter.class
)
public final class ApplicationJdbcTypeMappings
        implements JdbcTypeMappings {
}
```

Use a complete official collection as the base and select at most one explicit application override from the same package annotation:

```java
@UseJdbcTypeMappings(
    value = PostgreSqlJdbcTypeMappings.class,
    overrides = ApplicationJdbcTypeMappings.class
)
package com.example.account.mapper;
```

Compilation loads the base first and the application collection second. The application declaration replaces the exact same Java-type and `JDBCType` key; a new key is appended. The base collection still owns inferred defaults, so appending an alternative representation never changes an undeclared parameter or result implicitly; select the alternative with explicit `jdbcType` metadata. Duplicate keys inside either collection are errors. No collection is appended or discovered implicitly, and no runtime map participates in execution.

Use this path when the rule should apply consistently across Mapper methods in the same database package. Examples include PostgreSQL native UUID values, a project-wide `Money` representation, or an enum representation selected by an explicit JDBC type.

A JDBC value adapter works with one value at a time. It is not appropriate for combining several columns into an aggregate object.

## Generated Result Mapping and MyBatis `resultMap`

Generated result mapping and JDBC type mapping are layers, not alternatives. Generated mapping decides which result column supplies each constructor argument or property. A JDBC value adapter decides how one of those column values becomes its Java value.

```text
one result row -> generated record/JavaBean mapping
                  |-- id column      -> Long value mapping
                  |-- balance column -> Money JDBC value adapter
                  `-- status column  -> enum JDBC value adapter
```

Consequently, a declarative whole-row mapping cannot replace JDBC type mappings: it still needs a conversion for every non-built-in column value, and it has no role in Mapper parameter binding. Conversely, JDBC type mappings cannot describe how several columns are assembled into one object.

LiteORM currently generates scalar, record, and JavaBean result mapping directly. MyBatis XML `resultMap` declarations, including complex graphs, are not a supported parallel mapping engine. Flatten a simple shape to a record or JavaBean; use `@UseRowMapper` when the row requires logic that generated mapping cannot express.

## Parameter Binder: One Exceptional Write

`ParameterBinder<T>` belongs to one annotated Mapper parameter:

```java
int insert(
    @UseParameterBinder(BinaryUuidBinder.class)
    UUID id
);
```

Use it when only this parameter needs special treatment—for example, one UUID parameter stored as vendor-specific binary data while the package normally uses a string representation.

A parameter binder is write-only. It does not define how query results are read. If the same Java/JDBC representation should be reused for both writes and scalar reads, define a JDBC value adapter instead.

## Row Mapper: One Exceptional Read Shape

`RowMapper<T>` is the method-level, read-only escape hatch for one query:

```java
@UseRowMapper(UserSummaryRowMapper.class)
@Select("SELECT u.id, u.name, count(o.id) AS order_count FROM users u ...")
UserSummary findSummary(long id);
```

Use it when one result object needs custom construction from the current row, especially when several columns participate or the target cannot follow LiteORM's record and JavaBean rules.

A row mapper is read-only and method-specific. It should not be used to establish a reusable JDBC representation for a scalar Java type.

## Lifecycle-Bound JDBC Values

Driver objects such as `Blob`, `Clob`, `NClob`, `SQLXML`, and JDBC `Array` are valid only while their JDBC lifecycle remains open. LiteORM therefore exposes ordinary results as detached Java values:

| JDBC value | Mapper result | Requirement |
| --- | --- | --- |
| `BLOB` | `byte[]` | Declare `@ResultJdbcType(JDBCType.BLOB)` and select a collection that supports it. |
| `CLOB` / `NCLOB` | `String` | Declare the matching result JDBC type and select a supporting collection. |
| `SQLXML` | `String` | Declare `@ResultJdbcType(JDBCType.SQLXML)` and select a supporting collection. |
| `ARRAY` | `Object[]` | Declare `@ResultJdbcType(JDBCType.ARRAY)`; support is database-specific. |

LiteORM materializes the value and releases the driver resource before closing the ResultSet, statement, and connection handle. There is no hidden materialization threshold: the practical bounds are available JVM memory and the maximum Java array or string size. Use streaming for values that should not be fully retained in memory.

An `InputStream` or `Reader` cannot be a direct, list, or optional Mapper result. Consume it inside the existing cursor callback scope with a method-level row mapper:

```java
@UseRowMapper(BinaryStreamRowMapper.class)
@Select("SELECT payload FROM documents")
Long consume(CursorCallback<InputStream, Long> callback);
```

The row mapper obtains the stream from the current ResultSet row; the callback must finish consuming it before returning. Do not retain the stream, reader, or cursor after the callback. JDBC ARRAY parameters are also explicit: annotate the whole parameter with `@UseParameterBinder` so application code can supply the database element type and create the correct driver array.

### Does a Row Mapper Replace JDBC Type Mappings?

Only on the result side of the annotated query method.

```java
@UseRowMapper(UserRowMapper.class)
@Select("SELECT id, name FROM users WHERE status = #{status}")
List<User> findByStatus(Status status);
```

For this method:

- the package's JDBC type mappings still bind `status`;
- `UserRowMapper` replaces generated result mapping for each current row;
- JDBC type mappings continue to serve every other Mapper method in the package.

A row mapper reads the `ResultSet` directly. LiteORM does not automatically apply package JDBC value adapters inside custom row-mapper code. If a query has no parameters and a row mapper handles every result column, the package mappings do no runtime work for that particular method, although the package still has one compile-time mapping selection.

## Compilation and Runtime Responsibilities

```mermaid
flowchart LR
    subgraph Compile_time[Compilation]
        JM[Mapper declarations]
        PKG[Package JDBC mappings]
        PB[Parameter binder selection]
        RM[Row mapper selection]
        PROC[LiteORM processor]
        GEN[Generated Mapper implementation]

        JM --> PROC
        PKG --> PROC
        PB --> PROC
        RM --> PROC
        PROC --> GEN
    end

    subgraph Runtime[Runtime execution]
        CALL[Mapper call]
        PLAN[Generated execution plan]
        EXEC[SqlExecutor]
        PS[PreparedStatement]
        RS[ResultSet]
        RESULT[Java result]

        CALL --> GEN
        GEN --> PLAN
        PLAN --> EXEC
        EXEC -->|invokes generated binder or value adapter| PS
        EXEC --> RS
        RS -->|generated mapping, value adapter, or row mapper| RESULT
    end
```

The runtime distinction is therefore mechanical:

- parameter binding ends at `PreparedStatement`;
- value adapters may participate in parameter binding or single-column reading;
- row mapping starts from the current `ResultSet` row and produces the method's result element.

## Practical Decision Rules

- Prefer generated mapping first.
- Use a JDBC value adapter when the representation is a reusable property of a Java type within one database package.
- Let generated scalar, record, or JavaBean mapping compose those atomic column values for ordinary query results.
- Use a parameter binder when the exception belongs to one parameter rather than the package's type policy.
- Use a row mapper only as the method-level, read-only escape hatch when the exception belongs to one result shape rather than one column value.
- A row mapper replaces only the annotated method's result mapping; it does not replace parameter binding or the package's mapping policy.
- Use callback-scoped cursor consumption for supported stream/reader results; use raw JDBC when the application must own vendor resources or lifecycle behavior outside these interfaces.

The exact validation, lifecycle, null-binding, visibility, and concurrency requirements remain defined by the [Extension Contracts](../../reference/extensions.md).
