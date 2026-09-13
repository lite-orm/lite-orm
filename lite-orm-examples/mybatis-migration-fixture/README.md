# MyBatis Migration Fixture

This is a small, independently built validation fixture for the
`liteorm-mybatis-migration` agent skill. It is not a root Maven module and it
does not add MyBatis to any LiteORM runtime artifact.

The fixture is derived from the `org.mybatis.spring.sample.mapper.UserMapper`
example in `mybatis/spring` tag `mybatis-spring-3.0.6`,
commit `1ac0287cd0f6c9c778059487b39e0770daa3bc05`. The upstream project is
licensed under Apache License 2.0. See [UPSTREAM.md](UPSTREAM.md) and the
original source files under `migration/before/`.

## Contents

- `migration/before/`: the original static Mapper interface and XML statement;
- `src/main/`: the reviewed LiteORM conversion;
- `migration-report.md`: construct classification and intervention record;
- `pom.xml`: independent Java 21 consumer build.

The original Spring `MapperFactoryBean` and `SqlSession` wiring is intentionally
not converted here. LiteORM owns generated Mapper assembly and JDBC execution,
while the framework-owned wiring requires an application decision.

## Verify

From the repository root, install the local Core and processor artifacts:

```bash
mvn -pl lite-orm-processor -am -DskipTests install
```

Then build this fixture independently:

```bash
mvn -f lite-orm-examples/mybatis-migration-fixture/pom.xml clean verify
```

The build runs annotation processing and compiles
`GeneratedMapperConsumer`, which constructs the generated Mapper from the
public `SqlExecutor` contract. A database is not required for this
compile-time fixture; database-backed behavior remains covered by the Core and
processor Testcontainers suites.
