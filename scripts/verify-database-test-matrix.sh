#!/usr/bin/env bash
set -euo pipefail

suites=(
  org.liteorm.testsupport.database.TestDatabaseTest
  org.liteorm.test.database.PostgresCompatibilityTest
  org.liteorm.test.database.MySqlCompatibilityTest
  org.liteorm.test.database.PostgresCoreConcurrencySoakTest
  org.liteorm.test.database.MySqlCoreConcurrencySoakTest
  org.liteorm.test.multidatasource.PostgresMultiDataSourceExecutionTest
  org.liteorm.test.multidatasource.MySqlMultiDataSourceExecutionTest
  org.liteorm.types.postgresql.PostgreSqlUuidMappingTest
  org.liteorm.types.postgresql.PostgreSqlLocalTimeMappingTest
  org.liteorm.types.postgresql.PostgreSqlOffsetDateTimeMappingTest
  org.liteorm.types.postgresql.PostgreSqlOffsetTimeMappingTest
  org.liteorm.types.postgresql.PostgreSqlStandardScalarMappingTest
  org.liteorm.types.postgresql.PostgreSqlTemporalExclusionEvidenceTest
  org.liteorm.types.postgresql.PostgreSqlCompositeMappingTest
  org.liteorm.types.postgresql.PostgreSqlLifecycleBoundMappingTest
  org.liteorm.types.mysql.MySqlUuidMappingTest
  org.liteorm.types.mysql.MySqlLocalTimeMappingTest
  org.liteorm.types.mysql.MySqlOffsetDateTimeMappingTest
  org.liteorm.types.mysql.MySqlStandardScalarMappingTest
  org.liteorm.types.mysql.MySqlTemporalExclusionEvidenceTest
  org.liteorm.types.mysql.MySqlCompositeMappingTest
  org.liteorm.types.mysql.MySqlLifecycleBoundMappingTest
  org.liteorm.spring.boot.PostgresPackageDataSourceExecutionTest
  org.liteorm.spring.boot.MySqlPackageDataSourceExecutionTest
  org.liteorm.spring.boot.PostgresLiteOrmAutoConfigurationTest
  org.liteorm.spring.boot.MySqlLiteOrmAutoConfigurationTest
  org.liteorm.example.PostgresStandaloneJdbcUsageTest
  org.liteorm.example.MySqlStandaloneJdbcUsageTest
)

failed=0

for suite in "${suites[@]}"; do
  report="$(find . -path "*/target/surefire-reports/TEST-${suite}.xml" -print -quit)"
  if [[ -z "$report" ]]; then
    echo "Missing Surefire report for ${suite}" >&2
    failed=1
    continue
  fi

  tests="$(sed -n 's/.*<testsuite[^>]* tests="\([0-9][0-9]*\)".*/\1/p' "$report" | head -n 1)"
  failures="$(sed -n 's/.*<testsuite[^>]* failures="\([0-9][0-9]*\)".*/\1/p' "$report" | head -n 1)"
  errors="$(sed -n 's/.*<testsuite[^>]* errors="\([0-9][0-9]*\)".*/\1/p' "$report" | head -n 1)"
  skipped="$(sed -n 's/.*<testsuite[^>]* skipped="\([0-9][0-9]*\)".*/\1/p' "$report" | head -n 1)"

  if [[ -z "$tests" || -z "$failures" || -z "$errors" || -z "$skipped" ]]; then
    echo "Unreadable Surefire summary for ${suite}: ${report}" >&2
    failed=1
  elif (( tests == 0 || failures != 0 || errors != 0 || skipped != 0 )); then
    echo "Invalid database result for ${suite}: tests=${tests}, failures=${failures}, errors=${errors}, skipped=${skipped}" >&2
    failed=1
  else
    echo "Verified ${suite}: tests=${tests}, skipped=0"
  fi
done

exit "$failed"
