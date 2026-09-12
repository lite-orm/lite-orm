# MyBatis Compatibility

LiteORM targets the common Mapper subset that can be validated and emitted as static Java during compilation. It is not a drop-in replacement for every MyBatis runtime feature.

## JDBC Type Compatibility Baseline

MyBatis 3.5.19 built-in TypeHandlers are the comparison baseline for deterministic JDBC value behavior. LiteORM does not copy the MyBatis registry architecture: Core owns one fixed `TypeHandlerManager`, generated code supplies Java types, and runtime result routing uses live JDBC metadata.

The built-in matrix supports numeric primitives and wrappers, `String`, `Character`, `Boolean`, enums, `BigDecimal`, `BigInteger`, `LocalDate`, `LocalDateTime`, `Instant`, `UUID`, `LocalTime`, `OffsetDateTime`, `byte[]`, boxed `Byte[]`, legacy date values, `Year`, `Month`, `YearMonth`, and `JapaneseDate`. PostgreSQL and MySQL execute the shared Core compatibility contract with zero skipped database jobs in CI.

Parameter placeholders may choose a compatible `jdbcType`, such as `INTEGER` for enum ordinals, a character representation for UUID, or `DATE` / `TIME` for the matching legacy `java.util.Date` representation. Results need no JDBC-type annotation: character enum values use names and numeric values use ordinals.

LiteORM has no `TypeHandlerRegistry`, package mapping selection, vendor-type registration, database-specific type artifact, or `UnknownTypeHandler` fallback. Unsupported writes use a parameter-level `ParameterBinder`; unsupported reads or row shapes use a method-level `RowMapper`.

Lifecycle-bound values such as SQLXML, JDBC arrays, Blob, Clob, streams, and readers are not ordinary scalar results because LiteORM closes JDBC resources before Mapper results escape. Consume them through `RowMapper` or raw JDBC.

## Supported Annotation Patterns

All Mapper annotations are LiteORM-owned APIs in `org.liteorm.annotation`. LiteORM does not define compatibility classes under `org.apache.ibatis.annotations`.

| Pattern | Status | Notes |
| --- | --- | --- |
| `org.liteorm.annotation.Mapper` interfaces | Supported | Generates a concrete `*MapperImpl`. |
| `@Select`, `@Insert`, `@Update`, `@Delete` | Supported | Static SQL and supported `<script>` dynamic SQL compile to Java. |
| `@Param` | Supported | Prefer explicit names for multi-parameter methods. |
| `@Results`, `@Result` | Supported for flat mappings | Maps result columns to scalar results, record components, or JavaBean properties. Nested associations and collections are not supported. |
| Result-side JDBC type annotation | Not needed | The executor selects result handlers from JDBC metadata and the generated Java target type. |
| `param1`, `arg0`, `list`, `collection`, `array` aliases | Supported | Resolved during compilation. |
| Scalar results | Supported | Includes scalar `List<T>`. |
| Java records | Supported | Constructor mapping is generated from record components. |
| JavaBeans | Supported | Requires a usable no-arg constructor and supported setters. |
| SQL provider | Explicit extension | Use `@UseSqlProvider` for runtime SQL structure. |
| Custom JDBC conversion | Explicit extension | Use one-parameter `@UseParameterBinder` for writes or method-level `@UseRowMapper` for reads. |

When XML and a SQL annotation define the same Mapper method, XML wins because it can express richer SQL structure. The annotation processor emits a method-scoped warning.

## Supported XML Patterns

- Statements: `select`, `insert`, `update`, `delete`.
- Dynamic tags: `if`, `choose`, `when`, `otherwise`, `trim`, `where`, `set`, `foreach`, `bind`, `sql`, `include`.
- Real JDBC batch: LiteORM `@Batch` or XML `<batch>` with one `List<T>` argument and an `int[]` return value.
- Results declared with `resultType` when the Java return type can be mapped as a scalar, record, JavaBean, or list of one of those types.
- Flat `resultMap` declarations for scalar values, JavaBean `<id>` / `<result>` properties, and record `<constructor>` arguments. Constructor arguments may use `name` or record-component order.
- A controlled OGNL-like subset in `test` and `bind`: null, boolean, string and number comparisons; `and` and `or`; simple property paths; array `length`; collection `size()`; and string concatenation in `bind`.

The supported expression subset is translated directly into native Java. LiteORM does not embed OGNL, MVEL, SpEL, or another expression engine in annotation processing or Mapper execution. Unsupported expressions fail compilation.

## Unsupported Features And Migration Paths

| MyBatis feature | LiteORM behavior | Suggested migration |
| --- | --- | --- |
| `${}` SQL substitution | Compilation error | Use `@UseSqlProvider` with validated identifiers or write raw JDBC. |
| Arbitrary OGNL or static/method calls | Compilation error | Rewrite with the supported expression subset or move structure to a provider. |
| Complex `resultMap` graphs | Compilation error | Use a flat `resultMap`, `@UseRowMapper`, split the query, or use raw JDBC. |
| Associations, collections, nested aggregation | Not supported | Use explicit follow-up queries, a custom row mapper for one-row shapes, or raw JDBC. |
| Lazy loading and nested selects | Not supported | Make loading explicit in application/service code. |
| MyBatis plugins | Not supported | Use `ExecutionInterceptor` for the narrow execution lifecycle. |
| Runtime Mapper proxies | Intentionally absent | Instantiate or inject the generated Mapper implementation. |
| Runtime XML reload or interpretation | Intentionally absent | Recompile after changing Mapper XML. |
| Second-level cache | Not supported | Use an application cache outside LiteORM. |
| Same Mapper bound to several DataSources | Intentionally absent | Split Mapper packages/interfaces by DataSource domain or bind one routing DataSource. |
| Core distributed transactions | Not supported | Use an external transaction system; LiteORM assemblies remain independent. |

## Executable Compatibility Fixtures

- Annotation CRUD, scalar, record, and JavaBean mapping: `lite-orm-examples/basic-mapper/src/main/java/org/liteorm/example/UserMapper.java`.
- XML dynamic query, `foreach`, and selective `set`: `lite-orm-examples/basic-mapper/src/main/resources/org/liteorm/example/UserXmlMapper.xml`.
- SQL provider: `lite-orm-examples/basic-mapper/src/main/java/org/liteorm/example/UserSearchProvider.java`.
- Custom binder and row mapper: `lite-orm-examples/basic-mapper/src/main/java/org/liteorm/example/UserMetadataMapper.java`.
- Execution interceptor: `lite-orm-examples/basic-mapper/src/main/java/org/liteorm/example/MigrationAuditInterceptor.java`.
- Standalone PostgreSQL/MySQL execution and rollback: `lite-orm-examples/basic-mapper/src/test/java/org/liteorm/example/StandaloneJdbcUsageTest.java` with its two engine subclasses.
- Unsupported complex `resultMap`: `lite-orm-core/src/test/resources/org/liteorm/test/diagnostics/ComplexResultMapMapper.xml`.

Run all migration fixtures from the repository root:

```bash
mvn -pl lite-orm-core,lite-orm-examples/basic-mapper -am test
```
