# Migration Fixture Report

## Scope

- Source: `mybatis/spring`, tag `mybatis-spring-3.0.6`
- Source commit: `1ac0287cd0f6c9c778059487b39e0770daa3bc05`
- License: Apache License 2.0
- MyBatis dependency: `3.5.19`
- Target: the independent fixture in this directory
- Mode: reviewed conversion of one static Mapper method

The full upstream project was inspected read-only. This fixture contains only
the representative `UserMapper` input needed to exercise a deterministic
conversion and the framework-boundary intervention.

## Worklist

| Boundary | Status | Dependencies | Planned action | Verification |
| --- | --- | --- | --- | --- |
| `UserMapper#getUser` and its static XML statement | done | LiteORM compatibility matrix | Convert XML-backed method to `@Mapper`/`@Select` with explicit `@Param` | Independent Maven `clean verify`; generated `UserMapperImpl` |
| Spring `MapperFactoryBean` / `SqlSession` wiring | needs-user-decision | Application Spring assembly contract | Choose an application-owned replacement; do not rewrite automatically | Not run; recorded as intervention |
| `getUsers` XML declaration without a matching reduced Java method | blocked | Source ownership decision | Confirm whether to migrate or omit from the reduced fixture | Not run; intentionally omitted |

Next batch: none for this reduced fixture. A full project migration would create
additional batches from the inventory before changing source files.

## Interaction Log

None. The fixture scope and the Spring/session intervention boundary were fixed
in the review plan before conversion.

## Decisions

| Location | Construct | Matrix status | LiteORM action | Evidence |
| --- | --- | --- | --- | --- |
| `migration/before/.../UserMapper.java` | Mapper interface with one XML-backed method | Deterministic conversion | Add LiteORM `@Mapper` and move the static statement to `@Select` | [Mapper API](../../docs/reference/mybatis-compatibility.md#mapper-api-and-sql-sources) |
| `migration/before/.../UserMapper.xml` | Static `select` with one scalar parameter and flat JavaBean result | Deterministic conversion | Preserve SQL and result type; use explicit `@Param` | [XML and result mapping](../../docs/reference/mybatis-compatibility.md#xml-and-dynamic-sql) |
| Upstream Spring sample wiring | Skill-assisted migration | Record intervention | Replace `MapperFactoryBean` and `SqlSession` ownership only after an application-level Spring assembly decision | [Spring boundary](../../docs/reference/mybatis-compatibility.md#sessions-transactions-spring-plugins-and-caches) |
| Upstream nested result-map examples | Explicit rejection | Leave for manual redesign | Use a flat query, explicit follow-up query, `RowMapper`, or raw JDBC | [Result mapping](../../docs/reference/mybatis-compatibility.md#result-mapping-and-execution) |

## Converted Scope

- `org.mybatis.spring.sample.mapper.UserMapper` now uses LiteORM annotations.
- The SQL predicate remains `where id = #{userId}` and returns the same `User`
  JavaBean shape.
- The XML statement is removed from the migrated source because its static
  behavior is represented by the annotation.
- `GeneratedMapperConsumer` constructs the generated implementation through
  `SqlExecutor`; no runtime proxy or reflection fallback is introduced.

## Interventions

- Spring `MapperFactoryBean`/`@MapperScan` registration and `SqlSession`
  lifecycle are application-owned decisions and are not rewritten automatically.
- The original XML also contains `getUsers`, but the reduced Java input has no
  corresponding method. It is intentionally excluded rather than silently
  invented.
- Database execution is not claimed by this compile-time fixture. The relevant
  PostgreSQL/MySQL behavior remains covered by LiteORM's existing Testcontainers
  suites.

## Verification

Run from the repository root:

```bash
mvn -pl lite-orm-processor -am -DskipTests install
mvn -f lite-orm-examples/mybatis-migration-fixture/pom.xml clean verify
```

Observed on September 13, 2026 from the repository root with OpenJDK 21.0.4:

- `mvn -pl lite-orm-processor -am -DskipTests install`: exit `0`.
- `mvn -f lite-orm-examples/mybatis-migration-fixture/pom.xml clean verify`: exit
  `0`; compiled 3 sources, generated and compiled
  `org.mybatis.spring.sample.mapper.UserMapperImpl`, and produced the fixture
  JAR.
- Fixture tests: no test sources; no tests were run.
- Database execution: not run; this fixture intentionally verifies the
  compile-time migration boundary only.
- `git diff --check`: exit `0`.

## Claim Boundary

This fixture validates one deterministic static XML-to-annotation conversion and
public generated-Mapper consumption. It does not claim automatic migration of
Spring wiring, sessions, nested result graphs, caches, plugins, arbitrary
OGNL, dynamic DataSource behavior, or the complete upstream project.
