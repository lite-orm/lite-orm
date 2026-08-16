# LiteORM JMH Benchmarks

This module compares Direct JDBC, generated LiteORM Mappers, and MyBatis using
the same H2 DataSource, schema, SQL parameters, connection/session lifecycle,
and consumed results.

Normal repository builds compile and test the fixtures but do not execute JMH.

Build the executable benchmark JAR:

```bash
mvn -pl lite-orm-benchmarks -am package
```

Run the recorded Core GA protocol:

```bash
java -jar lite-orm-benchmarks/target/benchmarks.jar \
  '.*Benchmark.*' \
  -prof gc \
  -rf json \
  -rff lite-orm-benchmarks/target/results/core-ga-baseline.json
```

The benchmark annotations define two JVM forks, three one-second warmup
iterations, five one-second measurement iterations, one thread, average-time
mode, and microseconds per operation.

Use short overrides only for fixture smoke testing:

```bash
java -jar lite-orm-benchmarks/target/benchmarks.jar \
  'ReadBenchmark.*Scalar' -wi 1 -i 1 -w 200ms -r 200ms -f 1
```

Profilers perturb timing. Keep the unprofiled timing run, `-prof gc` allocation
run, and optional `-prof jfr` CPU investigation as separate result files when
making optimization decisions.
