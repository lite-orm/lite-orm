#!/usr/bin/env bash
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
output="${1:-$root/dist/liteorm-skill.tar.gz}"
stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT

mkdir -p "$stage/liteorm-skill" "$(dirname "$output")"
cp -R "$root/skills/liteorm/." "$stage/liteorm-skill/"
printf '%s\n' "$(git describe --tags --always --dirty 2>/dev/null || echo unknown)" > "$stage/liteorm-skill/VERSION"
tar -czf "$output" -C "$stage" liteorm-skill
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$output" > "$output.sha256"
else
  shasum -a 256 "$output" > "$output.sha256"
fi
echo "Created $output"
