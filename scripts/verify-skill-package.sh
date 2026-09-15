#!/usr/bin/env bash
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
output="${1:-$root/dist/lynxus-skill.tar.gz}"

test -f "$output"
tar -tzf "$output" \
  | grep -Fx 'lynxus-skill/SKILL.md' >/dev/null
tar -tzf "$output" \
  | grep -Fx 'lynxus-skill/VERSION' >/dev/null

echo "Skill package verified: $output"
