#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
report_file="$(mktemp)"
trap 'rm -f "$report_file"' EXIT

cd "$repository_root"

mvn --batch-mode --no-transfer-progress \
  -pl lite-orm-postgresql-types \
  dependency:tree \
  -Dscope=runtime \
  -DoutputFile="$report_file" \
  -DappendOutput=false

forbidden_dependencies=(
  'org.freemarker:freemarker'
  'org.mybatis:mybatis'
  'org.postgresql:postgresql'
  'org.testcontainers:'
  'org.liteorm:lite-orm-core'
  'org.liteorm:lite-orm-test-support'
)

for dependency in "${forbidden_dependencies[@]}"; do
  if grep -F "$dependency" "$report_file" >/dev/null; then
    echo "Forbidden PostgreSQL types runtime dependency: $dependency" >&2
    exit 1
  fi
done

artifact_name="$(mvn --quiet -pl lite-orm-postgresql-types \
  help:evaluate -Dexpression=project.build.finalName -DforceStdout)"
jar_file="lite-orm-postgresql-types/target/${artifact_name}.jar"
if [[ ! -f "$jar_file" ]]; then
  mvn --batch-mode --no-transfer-progress \
    -pl lite-orm-postgresql-types \
    -am \
    -DskipTests \
    package
fi

jar_listing="$(jar tf "$jar_file")"
for forbidden_path in \
    'org/liteorm/compile/' \
    'freemarker/' \
    'org/apache/ibatis/' \
    'org/postgresql/' \
    'org/testcontainers/'; do
  if [[ "$jar_listing" == *"$forbidden_path"* ]]; then
    echo "Forbidden PostgreSQL types jar content: $forbidden_path" >&2
    exit 1
  fi
done

echo "Verified PostgreSQL types runtime dependency and jar boundaries"
