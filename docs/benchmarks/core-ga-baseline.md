# LiteORM Core GA Benchmark Baseline

Date: 2026-08-16

## Purpose

This is a measurement baseline, not an optimization proposal. It compares Direct JDBC, generated LiteORM Mappers, and MyBatis under one controlled in-process database fixture. No core cache or hot-path behavior was changed while producing it.

## Harness

- OpenJDK JMH 1.37
- MyBatis 3.5.19
- H2 2.3.232, one in-memory DataSource per JMH state
- JDK: Temurin 21.0.4+7 LTS
- OS: macOS 26.3.1, Apple M3, 16 GiB memory
- Mode: average time, microseconds per operation
- Threads: 1
- Forks: 2
- Warmup: 3 iterations × 1 second
- Measurement: 5 iterations × 1 second
- Allocation profiler: JMH `-prof gc`
- CPU evidence: separate JMH `-prof jfr` runs for LiteORM scalar, dynamic SQL, and batch
- Source parent: `68c01c9`; benchmark sources were added in the following benchmark commit

Each comparison uses the same H2 DataSource, schema, SQL parameters, connection or session ownership boundary, and consumed result shape. `BenchmarkFixtureTest` verifies equivalent results before measurement.

H2 isolates framework/JDBC-call overhead and keeps the run reproducible. These numbers are not PostgreSQL or MySQL latency claims; production database behavior remains covered by compatibility tests.

## Reproduction

```bash
mvn -pl lite-orm-benchmarks -am package

java -jar lite-orm-benchmarks/target/benchmarks.jar \
  '.*Benchmark.*' \
  -prof gc \
  -rf json \
  -rff lite-orm-benchmarks/target/results/core-ga-baseline.json
```

JFR evidence was collected separately because profilers perturb timing:

```bash
java -jar lite-orm-benchmarks/target/benchmarks.jar \
  'ReadBenchmark.liteOrmScalar|ReadBenchmark.liteOrmDynamic|WriteBenchmark.liteOrmBatch' \
  -prof 'jfr:dir=lite-orm-benchmarks/target/results/jfr' \
  -wi 2 -i 3 -w 1s -r 1s -f 1
```

## Time Results

Lower is better.

| Workload | Direct JDBC µs/op | LiteORM µs/op | MyBatis µs/op | LiteORM vs direct | LiteORM vs MyBatis |
| --- | ---: | ---: | ---: | ---: | ---: |
| Scalar query | 2.475 | 3.477 | 4.239 | +40.5% | -18.0% |
| Record mapping | 2.940 | 3.916 | 5.803 | +33.2% | -32.5% |
| JavaBean mapping | 2.803 | 3.946 | 5.924 | +40.8% | -33.4% |
| Dynamic SQL, one row | 25.184 | 27.013 | 28.455 | +7.3% | -5.1% |
| Cursor, ten rows | 3.226 | 3.362 | 8.969 | +4.2% | -62.5% |
| Local transaction + scalar | 2.680 | 4.146 | 4.284 | +54.7% | -3.2% |
| JDBC batch, 100 rows | 40.021 | 41.319 | 71.573 | +3.2% | -42.3% |
| Generated key | 2.766 | 3.229 | 4.772 | +16.7% | -32.3% |

One no-op `ExecutionInterceptor` changes the LiteORM Record workload from 3.916 to 3.942 µs/op, approximately +0.7% in this fixture.

## Allocation Results

Lower is better. Values are JMH `gc.alloc.rate.norm` bytes per operation.

| Workload | Direct JDBC B/op | LiteORM B/op | MyBatis B/op |
| --- | ---: | ---: | ---: |
| Scalar query | 7,740 | 11,408 | 12,492 |
| Record mapping | 8,316 | 12,356 | 15,780 |
| JavaBean mapping | 8,184 | 12,380 | 15,756 |
| Dynamic SQL, one row | 32,488 | 38,312 | 39,392 |
| Cursor, ten rows | 9,168 | 9,624 | 24,696 |
| Local transaction + scalar | 8,520 | 12,644 | 12,528 |
| JDBC batch, 100 rows | 353,305 | 366,161 | 465,471 |
| Generated key | 21,790 | 23,602 | 26,054 |

The no-op interceptor adds approximately 28 B/op to the comparable LiteORM Record path.

## JFR Observations

The representative JFR recordings show:

- dynamic SQL samples are dominated by H2 SQL parsing and query execution;
- batch samples are dominated by H2 batch execution, MVStore updates, and transaction logging;
- scalar samples include H2 execution plus LiteORM `SqlResult.copyRows` and `getQueryResults` defensive-copy paths.

These observations identify investigation candidates only. The JFR profiling run has high timing variance and its timings must not replace the unprofiled/GC-profile baseline.

## Interpretation

- LiteORM remains above Direct JDBC, as expected for plan construction, lifecycle observation, defensive result ownership, and generated return mapping.
- LiteORM is below MyBatis in every measured mapping, cursor, batch, generated-key, and scalar workload in this fixture.
- Dynamic SQL and standalone transaction results are close to MyBatis; database parsing/execution and common connection ownership dominate more of those operations.
- Cursor and batch paths are closest to Direct JDBC, suggesting the fixed executor lifecycle is not adding a large multiplicative cost for multi-row work.
- The largest LiteORM-versus-direct percentage gaps are tiny scalar/mapping and transaction operations where fixed per-call allocations are most visible.

## Optimization Gate

No optimization is approved by this baseline alone. A follow-up change must:

1. select one measured workload and state the expected improvement;
2. retain all correctness and ownership contracts;
3. add no global mutable cache;
4. rerun the same JMH protocol before and after the change;
5. include allocation and CPU evidence;
6. reject the change if the improvement is within noise or shifts cost into another required path.
