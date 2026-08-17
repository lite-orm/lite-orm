#!/usr/bin/env bash
set -euo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
version_file="${SUPERPOWERS_VERSION_FILE:-$repository_root/.agent-tools/superpowers.version}"
codex_home="${CODEX_HOME:-$HOME/.codex}"
agent_skills_home="${AGENT_SKILLS_HOME:-$HOME/.agents/skills}"

if [[ ! -f "$version_file" ]]; then
    echo "Missing Superpowers version lock: $version_file" >&2
    exit 1
fi

required_version="$(tr -d '[:space:]' <"$version_file")"
if [[ ! "$required_version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Invalid Superpowers version lock: $required_version" >&2
    exit 1
fi

installations=()

read_manifest_version() {
    local manifest="$1"
    sed -n 's/^[[:space:]]*"version"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' "$manifest" | head -n 1
}

record_installation() {
    local source="$1"
    local manifest="$2"
    local version

    if [[ ! -f "$manifest" ]]; then
        echo "Active Superpowers source has no manifest: $source" >&2
        exit 1
    fi

    version="$(read_manifest_version "$manifest")"
    if [[ -z "$version" ]]; then
        echo "Cannot read Superpowers version from $manifest" >&2
        exit 1
    fi

    installations+=("$source|$version|$manifest")
}

find_codex_manifest() {
    local source="$1"
    local plugin_root="$codex_home/plugins/cache/$source/superpowers"
    local manifests=()
    local manifest

    for manifest in "$plugin_root"/*/.codex-plugin/plugin.json; do
        [[ -f "$manifest" ]] && manifests+=("$manifest")
    done

    if [[ "${#manifests[@]}" -eq 0 ]]; then
        echo "Active Codex plugin has no cached manifest: superpowers@$source" >&2
        exit 1
    fi

    if [[ "${#manifests[@]}" -gt 1 ]]; then
        echo "Cannot determine the active Superpowers cache for superpowers@$source" >&2
        exit 1
    fi

    printf '%s\n' "${manifests[0]}"
}

if [[ -f "$codex_home/config.toml" ]]; then
    while IFS= read -r selector; do
        source="${selector#superpowers@}"
        manifest="$(find_codex_manifest "$source")"
        record_installation "Codex plugin $selector" "$manifest"
    done < <(
        awk '
            /^\[plugins\."superpowers@[^\"]+"\]$/ {
                selector = $0
                sub(/^\[plugins\."/, "", selector)
                sub(/"\]$/, "", selector)
                next
            }
            /^\[/ { selector = "" }
            selector != "" && /^[[:space:]]*enabled[[:space:]]*=[[:space:]]*true[[:space:]]*$/ {
                print selector
            }
        ' "$codex_home/config.toml"
    )
fi

legacy_skills="$agent_skills_home/superpowers"
if [[ -e "$legacy_skills" ]]; then
    resolved_skills="$(cd "$legacy_skills" && pwd -P)"
    record_installation "Agent skills link $legacy_skills" "$(dirname "$resolved_skills")/.codex-plugin/plugin.json"
fi

if [[ -n "${SUPERPOWERS_VERSION:-}" ]]; then
    installations+=("External agent installation|$SUPERPOWERS_VERSION|SUPERPOWERS_VERSION")
fi

if [[ "${#installations[@]}" -eq 0 ]]; then
    echo "Superpowers is not installed or could not be detected." >&2
    echo "Required version: $required_version" >&2
    exit 1
fi

if [[ "${#installations[@]}" -gt 1 ]]; then
    echo "Detected multiple active Superpowers installations:" >&2
    for installation in "${installations[@]}"; do
        IFS='|' read -r source version manifest <<<"$installation"
        echo "- $source: $version ($manifest)" >&2
    done
    echo "Keep exactly one installation at version $required_version." >&2
    exit 1
fi

IFS='|' read -r source installed_version manifest <<<"${installations[0]}"
if [[ "$installed_version" != "$required_version" ]]; then
    echo "LiteORM requires Superpowers $required_version but found $installed_version." >&2
    echo "Installation: $source ($manifest)" >&2
    exit 1
fi

echo "Superpowers $installed_version is installed through $source."
