#!/usr/bin/env bash
set -euo pipefail

suites=(
  io.github.lynxus.testsupport.database.TestDatabaseTest
  io.github.lynxus.test.database.PostgresCompatibilityTest
  io.github.lynxus.test.database.MySqlCompatibilityTest
  io.github.lynxus.test.database.PostgresCoreConcurrencySoakTest
  io.github.lynxus.test.database.MySqlCoreConcurrencySoakTest
  io.github.lynxus.test.multidatasource.PostgresMultiDataSourceExecutionTest
  io.github.lynxus.test.multidatasource.MySqlMultiDataSourceExecutionTest
  io.github.lynxus.spring.boot.PostgresPackageDataSourceExecutionTest
  io.github.lynxus.spring.boot.MySqlPackageDataSourceExecutionTest
  io.github.lynxus.spring.boot.PostgresLynxusAutoConfigurationTest
  io.github.lynxus.spring.boot.MySqlLynxusAutoConfigurationTest
  io.github.lynxus.example.PostgresStandaloneJdbcUsageTest
  io.github.lynxus.example.MySqlStandaloneJdbcUsageTest
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
