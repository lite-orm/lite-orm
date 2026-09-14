#!/usr/bin/env bash
set -euo pipefail

suites=(
  io.github.kervix.testsupport.database.TestDatabaseTest
  io.github.kervix.test.database.PostgresCompatibilityTest
  io.github.kervix.test.database.MySqlCompatibilityTest
  io.github.kervix.test.database.PostgresCoreConcurrencySoakTest
  io.github.kervix.test.database.MySqlCoreConcurrencySoakTest
  io.github.kervix.test.multidatasource.PostgresMultiDataSourceExecutionTest
  io.github.kervix.test.multidatasource.MySqlMultiDataSourceExecutionTest
  io.github.kervix.spring.boot.PostgresPackageDataSourceExecutionTest
  io.github.kervix.spring.boot.MySqlPackageDataSourceExecutionTest
  io.github.kervix.spring.boot.PostgresKervixAutoConfigurationTest
  io.github.kervix.spring.boot.MySqlKervixAutoConfigurationTest
  io.github.kervix.example.PostgresStandaloneJdbcUsageTest
  io.github.kervix.example.MySqlStandaloneJdbcUsageTest
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
