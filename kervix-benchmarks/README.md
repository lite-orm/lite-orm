# Kervix JMH Benchmarks

This module compares Direct JDBC, generated Kervix Mappers, and MyBatis using
the same DataSource, schema, SQL parameters, connection/session lifecycle, and
consumed results. H2 remains the fast local fixture; MySQL 8.4 is available for
database-realistic runs through Testcontainers.

Normal repository builds compile and test the fixtures but do not execute JMH.

Build the executable benchmark JAR:

```bash
mvn -pl kervix-benchmarks -am package
```

Run the recorded Core GA protocol:

```bash
java -jar kervix-benchmarks/target/benchmarks.jar \
  '.*Benchmark.*' \
  -prof gc \
  -rf json \
  -rff kervix-benchmarks/target/results/core-ga-baseline.json
```

Run against a real MySQL 8.4 container (Docker required):

```bash
java -Dbenchmark.database=mysql \
  -jar kervix-benchmarks/target/benchmarks.jar \
  '.*Benchmark.*' -rf json \
  -rff kervix-benchmarks/target/results/mysql.json
```

The MySQL mode starts one disposable `mysql:8.4` container per JVM process,
creates the benchmark schema, and seeds 1,000 rows. It is intentionally opt-in
so ordinary Maven tests stay deterministic and do not require Docker.

The benchmark annotations define two JVM forks, three one-second warmup
iterations, five one-second measurement iterations, one thread, average-time
mode, and microseconds per operation.

Use short overrides only for fixture smoke testing:

```bash
java -jar kervix-benchmarks/target/benchmarks.jar \
  'ReadBenchmark.*Scalar' -wi 1 -i 1 -w 200ms -r 200ms -f 1
```

Profilers perturb timing. Keep the unprofiled timing run, `-prof gc` allocation
run, and optional `-prof jfr` CPU investigation as separate result files when
making optimization decisions.
