# MyBatis Migration Skill Validation

This record captures the first validation slice for
`skills/liteorm/`. It is evidence for the skill workflow, not
a claim of broad MyBatis compatibility. The reproducible fixture now lives in
[`lite-orm-examples/mybatis-migration-fixture`](../../lite-orm-examples/mybatis-migration-fixture/README.md);
the `/tmp` checkout is only the read-only upstream source used to prepare it.

## Validation Subject

- Project: [`mybatis/spring`](https://github.com/mybatis/spring)
- Tag: `mybatis-spring-3.0.6`
- Pinned commit: `1ac0287cd0f6c9c778059487b39e0770daa3bc05`
- License: Apache License 2.0, present in the checkout's `LICENSE`
- MyBatis dependency: `3.5.19`, declared by the pinned project
- Validation date: September 13, 2026

The checkout was used read-only. It contains real Mapper XML, Spring
`MapperFactoryBean` and `@MapperScan` wiring, SqlSession lifecycle code,
plugins, caches, and nested result-map examples. These constructs exercise the
skill's reporting boundaries, including cases that require manual review.

## Reproducible Workflow

The upstream source was cloned into `/tmp` with the repository's configured
proxy, then inventoried without modifying the checkout:

```bash
python3 skills/liteorm/scripts/scan_mybatis.py \
  --project /tmp/liteorm-mybatis-spring-3.0.6 \
  --output /tmp/liteorm-mybatis-spring-inventory.md
```

Result: exit status `0`; 205 text/build files scanned and 1,262 source signals
reported. The report contains line-level evidence for the project's MyBatis
dependencies, Mapper XML, `SqlSession` and Spring wiring, plugins/caches, and
nested result-map declarations. It explicitly says that signals are not
compatibility decisions.

The repository fixture under
[`lite-orm-examples/mybatis-migration-fixture`](../../lite-orm-examples/mybatis-migration-fixture/README.md)
now preserves the reviewed before/after path. Its validation edit:

- added LiteORM `@Mapper`, `@Select`, and explicit `@Param`;
- preserved the SQL predicate and result type;
- removed the XML file after moving its one deterministic statement to the
  annotation;
- left the surrounding Spring `MapperFactoryBean` wiring as a manual
  intervention rather than guessing a replacement.

The fixture contains the original input under `migration/before/`, the
migrated source under `src/main/`, and a standalone Maven build. Its inventory
completed with exit status `0`, scanning 7 files and reporting 7 source
signals. Maven property substitutions are intentionally excluded from the
MyBatis text-substitution signal. The resulting source diff is reproducible with:

```bash
diff -ruN \
  lite-orm-examples/mybatis-migration-fixture/migration/before \
  lite-orm-examples/mybatis-migration-fixture/src/main
```

The post-change inventory completed with exit status `0`. The original
licensed checkout remained clean according to `git status --short`.

## Validation Results

| Check | Result |
| --- | --- |
| Skill structure validator | Passed: `quick_validate.py` reported `Skill is valid!` (exit `0`, validator supplied by local skill tooling) |
| Python syntax | Passed: `python3 -m py_compile skills/liteorm/scripts/scan_mybatis.py` (exit `0`) |
| Offline inventory on the real checkout | Passed, exit status `0` |
| Repository fixture before/after diff | Passed, reviewable diff preserved |
| Licensed checkout mutation check | Passed, clean worktree |
| Fixture Maven verification | Passed: generated `UserMapperImpl`; both Maven commands exit status `0` under OpenJDK 21.0.4; fixture has no test sources and no database execution |

The fixture build was run with:

```bash
mvn -pl lite-orm-processor -am -DskipTests install
mvn -f lite-orm-examples/mybatis-migration-fixture/pom.xml clean verify
```

Both commands completed with exit status `0` under JDK 21. The fixture compiler
reported generation of `org.mybatis.spring.sample.mapper.UserMapperImpl`, and
the public `SqlExecutor` consumer compiled successfully. This is a
compile-time fixture; database execution remains outside its scope.

## Claim Boundary

This validation proves that the skill can inventory a licensed real project,
preserve source evidence, produce a deterministic reviewable transformation for
one static Mapper statement in a repository fixture, and stop at framework-owned
Spring/session semantics. It does not prove automatic conversion of the
project, nested result graphs, plugins, caches, arbitrary OGNL, dynamic
DataSource behavior, or any unsupported LiteORM construct.
