# Agent Tooling

## Responsibility Boundaries

Repository rules and agent workflows are separate concerns:

- `AGENTS.md` is the canonical project policy for every coding agent.
- Tool-specific files are thin adapters that point to `AGENTS.md`.
- Superpowers supplies reusable planning, TDD, debugging, review, and delivery workflows.
- Agent plugins must not redefine LiteORM architecture or override repository policy.

The repository must remain buildable, testable, and maintainable without an agent plugin. Superpowers standardizes how agents work; it is not a build dependency.

## Superpowers Version Lock

The required version is stored in:

```text
.agent-tools/superpowers.version
```

Use exactly that version and keep only one active Superpowers installation. Multiple active installations can expose duplicate skills with different behavior.

Verify the current environment before non-trivial agent-driven work:

```bash
scripts/check-agent-environment.sh
```

The checker detects enabled Codex plugins and the legacy `~/.agents/skills/superpowers` installation. Other agent clients may provide their verified version explicitly:

```bash
SUPERPOWERS_VERSION="$(cat .agent-tools/superpowers.version)" \
  scripts/check-agent-environment.sh
```

Only set `SUPERPOWERS_VERSION` after verifying the version reported by the client plugin manager.

## Codex Setup

Install Superpowers from one marketplace source only. The preferred source is:

```text
superpowers@superpowers-marketplace
```

Refresh the marketplace snapshot with:

```bash
codex plugin marketplace upgrade superpowers-marketplace
```

If the installed version does not match the repository lock, remove and reinstall it:

```bash
codex plugin remove superpowers@superpowers-marketplace
codex plugin add superpowers@superpowers-marketplace
```

Remove competing installations such as `superpowers@openai-curated` or a legacy `~/.agents/skills/superpowers` link before running the checker again.

## Other Agent Clients

Claude Code, Cursor, Copilot, Gemini, and other clients should use their native plugin or skill manager when available. Their repository-specific adapter must remain thin and must point back to `AGENTS.md`.

When a client cannot load Superpowers, follow the equivalent workflow manually:

1. clarify requirements before implementation;
2. write a focused implementation plan for multi-step work;
3. use a failing test before production behavior changes;
4. debug from evidence rather than guesses;
5. review the diff and run fresh verification before completion.

Lack of a plugin never relaxes project architecture, testing, documentation, or commit requirements.

## Updating the Lock

Do not track an unpinned `latest` version. Update Superpowers through a focused pull request:

1. review the upstream release notes;
2. update `.agent-tools/superpowers.version`;
3. install that version in one supported client;
4. run `scripts/test-check-agent-environment.sh`;
5. exercise the changed workflows on a representative task;
6. document any project-specific compatibility notes here.

The version-lock change must use an English Conventional Commit message.
