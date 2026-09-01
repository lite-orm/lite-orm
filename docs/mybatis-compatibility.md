# MyBatis Compatibility

LiteORM targets the common Mapper subset that can be validated and emitted as static Java during compilation. It is not a drop-in replacement for every MyBatis runtime feature.

## JDBC Type Compatibility Baseline

MyBatis 3.5.19 built-in TypeHandlers are the comparison baseline for deterministic JDBC value mappings. LiteORM preserves its compile-time architecture: each Mapper package explicitly selects one `JdbcTypeMappings` collection, concrete `JdbcValueAdapter` implementations are selected during compilation and referenced directly, and unknown Java values never fall through to a runtime `UnknownTypeHandler` equivalent.

The current built-in matrix supports numeric primitives and wrappers, `String`, `Character`, `Boolean`, enum names, `BigDecimal`, `LocalDate`, `LocalDateTime`, `Instant`, `UUID`, `LocalTime`, `OffsetDateTime`, and `byte[]`. PostgreSQL and MySQL execute the shared compatibility contract with zero skipped database jobs in CI.

Parity is not complete. The remaining deterministic MyBatis categories include:

- `BigInteger`, boxed-byte arrays, enum ordinal mapping, and legacy JDBC/date values;
- `OffsetTime`, `Year`, `Month`, `YearMonth`, `ZonedDateTime`, and `JapaneseDate`;
- national-character, SQLXML, JDBC array, Blob, Clob, stream, and reader handlers;
- package-scoped PostgreSQL and MySQL mapping collections plus the compile-time contracts required for user and third-party collections.

Connection-bound resources require lifecycle-safe semantics. LiteORM closes the result set, statement, and connection handle before a normal Mapper result escapes. Ordinary mappings materialize Blob values as `byte[]`, Clob and SQLXML values as `String`, and JDBC arrays as Java arrays. Streams and readers are consumed through an existing callback-scoped mapping path and never escape after cleanup.

The planned mapping contracts have deliberately separate roles: `JdbcTypeMappings` is a database-family collection, repeatable `@JdbcTypeMapping` declarations associate one Java type and JDBC type with one `JdbcValueAdapter`, and `@UseJdbcTypeMappings` selects the collection in a Mapper package's `package-info.java`. Selection is explicit; there is no command-line profile, classpath auto-detection, global registry, ServiceLoader lookup, or Mapper-level override. LiteORM initially ships only PostgreSQL and MySQL collections. Additional collections may be delivered through normal Maven or Gradle dependencies.

`ObjectTypeHandler` and `UnknownTypeHandler` behavior is deliberately not a parity target. Unsupported values fail compilation with guidance to use an explicit typed extension.

## Supported Annotation Patterns

All Mapper annotations are LiteORM-owned APIs in `org.liteorm.annotation`. LiteORM does not define compatibility classes under `org.apache.ibatis.annotations`.

| Pattern | Status | Notes |
| --- | --- | --- |
| `org.liteorm.annotation.Mapper` interfaces | Supported | Generates a concrete `*MapperImpl`. |
| `@Select`, `@Insert`, `@Update`, `@Delete` | Supported | Static SQL and supported `<script>` dynamic SQL compile to Java. |
| `@Param` | Supported | Prefer explicit names for multi-parameter methods. |
| `param1`, `arg0`, `list`, `collection`, `array` aliases | Supported | Resolved during compilation. |
| Scalar results | Supported | Includes scalar `List<T>`. |
| Java records | Supported | Constructor mapping is generated from record components. |
| JavaBeans | Supported | Requires a usable no-arg constructor and supported setters. |
| SQL provider | Explicit extension | Use `@UseSqlProvider` for runtime SQL structure. |
| Custom JDBC conversion | Explicit extension | Use `@UseParameterBinder` and `@UseRowMapper`. |

When XML and a SQL annotation define the same Mapper method, XML wins because it can express richer SQL structure. The annotation processor emits a method-scoped warning.

## Supported XML Patterns

- Statements: `select`, `insert`, `update`, `delete`.
- Dynamic tags: `if`, `choose`, `when`, `otherwise`, `trim`, `where`, `set`, `foreach`, `bind`, `sql`, `include`.
- Real JDBC batch: LiteORM `@Batch` or XML `<batch>` with one `List<T>` argument and an `int[]` return value.
- Results declared with `resultType` when the Java return type can be mapped as a scalar, record, JavaBean, or list of one of those types.
- A controlled OGNL-like subset in `test` and `bind`: null, boolean, string and number comparisons; `and` and `or`; simple property paths; array `length`; collection `size()`; and string concatenation in `bind`.

The supported expression subset is translated directly into native Java. LiteORM does not embed OGNL, MVEL, SpEL, or another expression engine in annotation processing or Mapper execution. Unsupported expressions fail compilation.

## Unsupported Features And Migration Paths

| MyBatis feature | LiteORM behavior | Suggested migration |
| --- | --- | --- |
| `${}` SQL substitution | Compilation error | Use `@UseSqlProvider` with validated identifiers or write raw JDBC. |
| Arbitrary OGNL or static/method calls | Compilation error | Rewrite with the supported expression subset or move structure to a provider. |
| Complex `resultMap` graphs | Compilation error | Flatten to `resultType`, use `@UseRowMapper`, split the query, or use raw JDBC. |
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
