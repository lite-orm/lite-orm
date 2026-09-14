#!/usr/bin/env bash
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
output="${1:-$root/dist/lynxus-skill.tar.gz}"
stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT

mkdir -p "$stage/lynxus-skill" "$(dirname "$output")"
cp -R "$root/skills/lynxus/." "$stage/lynxus-skill/"
printf '%s\n' "$(git describe --tags --always --dirty 2>/dev/null || echo unknown)" > "$stage/lynxus-skill/VERSION"
tar -czf "$output" -C "$stage" lynxus-skill
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$output" > "$output.sha256"
else
  shasum -a 256 "$output" > "$output.sha256"
fi
echo "Created $output"
