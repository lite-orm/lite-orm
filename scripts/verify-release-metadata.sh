#!/usr/bin/env bash
set -euo pipefail

repository_root="$(git rev-parse --show-toplevel)"
cd "$repository_root"

[[ -f LICENSE ]] || { echo "Missing Apache-2.0 LICENSE file" >&2; exit 1; }
grep -Fq 'Apache License' LICENSE || { echo "LICENSE is not Apache-2.0 text" >&2; exit 1; }
grep -Fq '<url>https://github.com/lynxus-project/lynxus</url>' pom.xml || {
  echo "Root POM is missing project URL" >&2
  exit 1
}
grep -Fq '<name>Apache License, Version 2.0</name>' pom.xml || {
  echo "Root POM is missing Apache-2.0 metadata" >&2
  exit 1
}
grep -Fq '<url>https://www.apache.org/licenses/LICENSE-2.0.txt</url>' pom.xml || {
  echo "Root POM is missing Apache-2.0 URL" >&2
  exit 1
}

echo "Release metadata verified: Apache-2.0, project URL, and root POM license metadata"
