#!/usr/bin/env bash
set -euo pipefail

message_file="${1:?usage: validate-commit-message.sh <commit-message-file>}"
subject="$(head -n 1 "$message_file")"

if [[ "$subject" == Merge\ * ]] || [[ "$subject" == Revert\ \"* ]]; then
    exit 0
fi

if LC_ALL=C grep -q '[^ -~]' <<<"$subject"; then
    echo "Commit subject must use printable ASCII English." >&2
    exit 1
fi

pattern='^(feat|fix|refactor|test|docs|build|ci|chore|perf|release|revert)(\([a-z0-9._/-]+\))?!?: [a-z0-9].{4,71}$'

if [[ ! "$subject" =~ $pattern ]]; then
    echo "Invalid commit subject: $subject" >&2
    echo "Expected: <type>(optional-scope): imperative English summary" >&2
    echo "Allowed types: feat fix refactor test docs build ci chore perf release revert" >&2
    exit 1
fi

case "$subject" in
    *": update"|*": changes"|*": wip"|*": stuff"|*": fix stuff")
        echo "Commit subject is too vague: $subject" >&2
        exit 1
        ;;
esac
