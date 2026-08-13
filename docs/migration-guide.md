# Migrating From MyBatis

## 1. Classify Each Mapper Method

Use this decision order:

1. **Generated built-in path:** annotations or XML using the supported static and dynamic SQL subset.
2. **Typed extension:** `SqlProvider`, `ParameterBinder`, `RowMapper`, or `ExecutionInterceptor` selected explicitly.
3. **Raw JDBC:** SQL or result construction that cannot be represented safely by the first two paths.

Do not start with a provider or raw JDBC only because the original Mapper used XML. Most common XML can remain XML and compile into static Java.

## 2. Migrate Annotation Mappers

- Replace MyBatis annotation imports with LiteORM-owned annotations from `org.liteorm.annotation`.
- Keep the common `@Mapper`, `@Select`, `@Insert`, `@Update`, and `@Delete` method structure after changing imports.
- Add explicit `@Param` names to multi-parameter methods.
- Keep return types to supported scalars, records, JavaBeans, and `List<T>` variants.
- Compile and inspect the generated `*MapperImpl` when diagnosing binding or mapping behavior.

The executable CRUD fixture is `lite-orm-examples/basic-mapper/src/main/java/org/liteorm/example/UserMapper.java`.

```java
import org.liteorm.annotation.Delete;
import org.liteorm.annotation.Insert;
import org.liteorm.annotation.Mapper;
import org.liteorm.annotation.Param;
import org.liteorm.annotation.Select;
import org.liteorm.annotation.Update;
```

## 3. Migrate XML Mappers

- Keep the XML resource beside the Mapper package path.
- Keep statement IDs equal to Mapper method names.
- Use the supported dynamic tags and expression subset.
- Replace `${}` with bound `#{}` values whenever the value is data rather than SQL structure.
- If XML and an annotation coexist for one method, verify the compiler warning and treat XML as the effective source.

The dynamic XML fixture is `lite-orm-examples/basic-mapper/src/main/resources/org/liteorm/example/UserXmlMapper.xml`. It covers `where`, `if`, `foreach`, and `set` with real H2 assertions.

Use LiteORM `@Batch` or XML `<batch>` when the desired behavior is JDBC `PreparedStatement.addBatch/executeBatch`. A normal XML `<insert>` with `<foreach>` remains one dynamically generated multi-value SQL statement rather than a JDBC batch.

## 4. Replace Runtime OGNL Assumptions

LiteORM does not execute OGNL. Supported OGNL-like syntax is translated to native Java during annotation processing.

Typical supported rewrites include:

```text
name != null and name != ''  ->  name != null && !name.isEmpty()
ids != null and ids.size() > 0  ->  ids != null && ids.size() > 0
values != null and values.length > 0  ->  values != null && values.length > 0
'%' + name + '%'  ->  "%" + name + "%"
```

Arbitrary method calls, static calls, expression-engine features, and unsafe SQL substitution fail compilation. Simplify the expression or choose a typed SQL provider.

## 5. Migrate Exceptional SQL Structure

Use `@UseSqlProvider` only when table names, selected columns, ordering, vendor syntax, or another SQL structural decision cannot use bound parameters or the supported XML tags.

- The provider class and generic input type are validated during compilation.
- Generated code constructs one provider and calls it directly.
- The provider returns `BoundSql` with ordered `BoundParameter` values.
- Provider methods accept zero or one argument; group multiple values in a record.

See `lite-orm-examples/basic-mapper/src/main/java/org/liteorm/example/UserSearchProvider.java`.

## 6. Migrate Type Handlers And Result Maps

- Replace exceptional parameter type handling with `@UseParameterBinder`.
- Replace custom one-row object construction with `@UseRowMapper`.
- Flatten simple `resultMap` usage into a supported record or JavaBean `resultType` where possible.
- Split nested graphs into explicit queries or use raw JDBC when aggregation spans multiple rows.

Complex XML `resultMap` declarations fail compilation with guidance instead of being ignored. The negative fixture is `lite-orm-core/src/test/resources/org/liteorm/test/diagnostics/ComplexResultMapMapper.xml`.

## 7. Migrate Plugins And Cross-Cutting Behavior

Use `ExecutionInterceptor` for the stable execution lifecycle rather than intercepting arbitrary Mapper or executor internals.

- `beforeExecution` runs in configured order.
- Success and failure callbacks unwind in reverse order.
- Interceptors observe final SQL and ordered parameter copies but cannot replace generated binders or row mappers.
- Spring Boot collects interceptor beans with Spring ordering.

See `lite-orm-examples/basic-mapper/src/main/java/org/liteorm/example/MigrationAuditInterceptor.java`.

## 8. Verify Migration

Run the compiler and integration fixtures:

```bash
mvn clean test
```

Treat compilation diagnostics as migration tasks. Do not add reflection or runtime expression interpretation to bypass them; select an explicit extension or raw JDBC boundary instead.
