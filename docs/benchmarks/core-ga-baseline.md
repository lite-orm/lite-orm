# LiteORM Core GA Benchmark Baseline

Date: 2026-09-13

## Purpose

This is a measurement baseline, not an optimization proposal. It compares Direct JDBC, generated LiteORM Mappers, and MyBatis under one controlled in-process database fixture. No core cache or hot-path behavior was changed while producing it.

## Harness

- OpenJDK JMH 1.37
- MyBatis 3.5.19
- MySQL 8.4 (`mysql:8.4` via Testcontainers), one disposable container per JVM process
- JDK: Temurin 21.0.4+7 LTS
- OS: macOS 26.3.1, Apple M3, 16 GiB memory
- Mode: average time, microseconds per operation
- Threads: 1
- Forks: 1 (refresh run; the standard protocol may use 2)
- Warmup: 2 iterations × 1 second
- Measurement: 3 iterations × 1 second
- Allocation profiler: JMH `-prof gc`
- CPU evidence: separate JMH `-prof jfr` runs for LiteORM scalar, dynamic SQL, and batch
- Source parent: `68c01c9`; benchmark sources were added in the following benchmark commit

Each comparison uses the same MySQL DataSource, schema, SQL parameters, connection or session ownership boundary, and consumed result shape. `BenchmarkFixtureTest` verifies equivalent results before measurement. H2 remains available as the fast default fixture; it must not be used to describe the MySQL results below.

## Reproduction

```bash
mvn -pl lite-orm-benchmarks -am package

java -Dbenchmark.database=mysql -jar lite-orm-benchmarks/target/benchmarks.jar \
  '.*Benchmark.*' \
  -rf json \
  -rff lite-orm-benchmarks/target/results/mysql-2026-09-13.json
```

The command starts a disposable MySQL 8.4 container and therefore requires a
running Docker daemon. The recorded run used the command above with two
one-second warmups, three one-second measurements, one fork, and one thread.

JFR evidence was collected separately because profilers perturb timing:

```bash
java -jar lite-orm-benchmarks/target/benchmarks.jar \
  'ReadBenchmark.liteOrmScalar|ReadBenchmark.liteOrmDynamic|WriteBenchmark.liteOrmBatch' \
  -prof 'jfr:dir=lite-orm-benchmarks/target/results/jfr' \
  -wi 2 -i 3 -w 1s -r 1s -f 1
```

## MySQL Time Results

Lower is better.

| Workload | Direct JDBC µs/op | LiteORM µs/op | MyBatis µs/op |
| --- | ---: | ---: | ---: |
| Scalar query | 3,511 | 3,362 | 3,563 |
| Record mapping | 3,404 | 3,423 | 3,580 |
| JavaBean mapping | 3,623 | 3,207 | 3,611 |
| Dynamic SQL, one row | 3,370 | 3,499 | 3,758 |
| Cursor, ten rows | 3,307 | 3,644 | 3,988 |
| Local transaction + scalar | 3,587 | 3,655 | 3,508 |
| JDBC batch, 100 rows | 117,654 | 121,036 | 13,220 |
| Generated key | 5,837 | 6,618 | 6,288 |

Values are microseconds per operation from the 2026-09-13 MySQL run. The
batch workload is not directly comparable to the read workloads because MyBatis
uses a different batching strategy in this fixture; investigate before drawing
conclusions from that row.

## Allocation Results

Allocation profiling was not part of this MySQL timing run. Run the same command
with `-prof gc` and publish the generated JSON before making allocation claims.

## JFR Observations

The representative JFR recordings show:

- JFR was not collected for this MySQL refresh; use `-prof jfr` as a separate run
  when CPU evidence is needed.

These observations identify investigation candidates only. The JFR profiling run has high timing variance and its timings must not replace the unprofiled/GC-profile baseline.

## Interpretation

- These results include network and server costs from a local MySQL container and
  are not a production-latency promise.
- The relatively wide confidence intervals reflect a short local run; repeat on
  a dedicated host before using small differences as optimization evidence.
- Compare frameworks only within the same workload and lifecycle boundary.

## Optimization Gate

No optimization is approved by this baseline alone. A follow-up change must:

1. select one measured workload and state the expected improvement;
2. retain all correctness and ownership contracts;
3. add no global mutable cache;
4. rerun the same JMH protocol before and after the change;
5. include allocation and CPU evidence;
6. reject the change if the improvement is within noise or shifts cost into another required path.
