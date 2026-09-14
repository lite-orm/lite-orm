# Agent Tooling

## Responsibility Boundaries

Repository rules and agent workflows are separate concerns:

- `AGENTS.md` is the canonical project policy for every coding agent.
- Tool-specific files are thin adapters that point to `AGENTS.md`.
- Agent plugins and skills must not redefine Kervix architecture or override repository policy.

The repository must remain buildable, testable, and maintainable without any agent plugin. Agent tooling standardizes how agents work; it is never a build dependency.

## Agent Clients

Claude Code, Cursor, Copilot, Gemini, Codex, and other clients should use their native plugin or skill manager when available. Each client's repository-specific adapter must remain thin and must point back to `AGENTS.md`.

Matt Pocock's engineering skills are the primary workflow. Use the smallest applicable flow:

1. use `grill-with-docs` when requirements or domain language are unresolved;
2. use `to-spec` to publish the agreed behavior to GitHub Issues;
3. use `to-tickets` for dependency-aware tracer bullets;
4. use `implement` with `tdd` for behavior changes;
5. use `diagnosing-bugs` for failures and `code-review` before delivery.

Lack of a plugin never relaxes project architecture, testing, documentation, or commit requirements.
