#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
checker="$repository_root/scripts/check-agent-environment.sh"
temporary_root="$(mktemp -d)"
trap 'rm -rf "$temporary_root"' EXIT

write_manifest() {
    local manifest_path="$1"
    local version="$2"

    mkdir -p "$(dirname "$manifest_path")"
    cat >"$manifest_path" <<JSON
{
  "name": "superpowers",
  "version": "$version"
}
JSON
}

run_check() {
    local case_root="$1"
    shift

    HOME="$case_root/home" \
        CODEX_HOME="$case_root/codex" \
        AGENT_SKILLS_HOME="$case_root/agent-skills" \
        SUPERPOWERS_VERSION_FILE="$case_root/superpowers.version" \
        "$checker" "$@"
}

assert_fails_with() {
    local expected_message="$1"
    shift
    local output

    if output="$($@ 2>&1)"; then
        echo "Expected command to fail: $*" >&2
        exit 1
    fi

    if [[ "$output" != *"$expected_message"* ]]; then
        echo "Expected failure containing: $expected_message" >&2
        echo "$output" >&2
        exit 1
    fi
}

matching_root="$temporary_root/matching"
mkdir -p "$matching_root"
printf '%s\n' '6.3.0' >"$matching_root/superpowers.version"
write_manifest "$matching_root/codex/plugins/cache/superpowers-marketplace/superpowers/6.3.0/.codex-plugin/plugin.json" '6.3.0'
cat >"$matching_root/codex/config.toml" <<'TOML'
[plugins."superpowers@superpowers-marketplace"]
enabled = true
TOML
run_check "$matching_root" >/dev/null

mismatch_root="$temporary_root/mismatch"
mkdir -p "$mismatch_root"
printf '%s\n' '6.3.0' >"$mismatch_root/superpowers.version"
write_manifest "$mismatch_root/codex/plugins/cache/superpowers-marketplace/superpowers/6.2.0/.codex-plugin/plugin.json" '6.2.0'
cat >"$mismatch_root/codex/config.toml" <<'TOML'
[plugins."superpowers@superpowers-marketplace"]
enabled = true
TOML
assert_fails_with 'requires Superpowers 6.3.0 but found 6.2.0' run_check "$mismatch_root"

duplicate_root="$temporary_root/duplicate"
mkdir -p "$duplicate_root"
printf '%s\n' '6.3.0' >"$duplicate_root/superpowers.version"
write_manifest "$duplicate_root/codex/plugins/cache/superpowers-marketplace/superpowers/6.3.0/.codex-plugin/plugin.json" '6.3.0'
write_manifest "$duplicate_root/codex/plugins/cache/openai-curated/superpowers/5.1.3/.codex-plugin/plugin.json" '5.1.3'
cat >"$duplicate_root/codex/config.toml" <<'TOML'
[plugins."superpowers@superpowers-marketplace"]
enabled = true

[plugins."superpowers@openai-curated"]
enabled = true
TOML
assert_fails_with 'multiple active Superpowers installations' run_check "$duplicate_root"

missing_root="$temporary_root/missing"
mkdir -p "$missing_root"
printf '%s\n' '6.3.0' >"$missing_root/superpowers.version"
assert_fails_with 'Superpowers is not installed' run_check "$missing_root"

echo "Agent environment checks passed"
