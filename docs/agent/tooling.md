# Agent Tooling

## Responsibility Boundaries

Repository rules and agent workflows are separate concerns:

- `AGENTS.md` is the canonical project policy for every coding agent.
- Tool-specific files are thin adapters that point to `AGENTS.md`.
- Agent plugins and skills must not redefine LiteORM architecture or override repository policy.

The repository must remain buildable, testable, and maintainable without any agent plugin. Agent tooling standardizes how agents work; it is never a build dependency.

## Agent Clients

Claude Code, Cursor, Copilot, Gemini, Codex, and other clients should use their native plugin or skill manager when available. Each client's repository-specific adapter must remain thin and must point back to `AGENTS.md`.

Whatever tooling a client provides, follow this workflow for non-trivial work:

1. clarify requirements before implementation;
2. write a focused implementation plan for multi-step work;
3. use a failing test before production behavior changes;
4. debug from evidence rather than guesses;
5. review the diff and run fresh verification before completion.

Lack of a plugin never relaxes project architecture, testing, documentation, or commit requirements.
