#!/usr/bin/env bash
set -euo pipefail

root="$(git rev-parse --show-toplevel)"
output="${1:-$root/dist/kervix-skill.tar.gz}"
stage="$(mktemp -d)"
trap 'rm -rf "$stage"' EXIT

mkdir -p "$stage/kervix-skill" "$(dirname "$output")"
cp -R "$root/skills/kervix/." "$stage/kervix-skill/"
printf '%s\n' "$(git describe --tags --always --dirty 2>/dev/null || echo unknown)" > "$stage/kervix-skill/VERSION"
tar -czf "$output" -C "$stage" kervix-skill
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$output" > "$output.sha256"
else
  shasum -a 256 "$output" > "$output.sha256"
fi
echo "Created $output"
