---
name: cloud-github
description: >-
  GitHub and pull-request workflows for Cursor Cloud Agents in this repo.
  Prefer built-in ManagePullRequest over gh CLI; use gh only after install.sh.
---

# Cloud GitHub operations

Cursor Cloud Agents in this repository must not fail because `gh` is missing from PATH.
`.cursor/install.sh` symlinks `/exec-daemon/gh` into `~/.local/bin/gh` and, when
`/usr/local/bin` is writable, into `/usr/local/bin/gh`. When `gh auth status` is unauthenticated,
it logs in with `GH_TOKEN` or `GITHUB_TOKEN` if set.

## PR create / update (preferred)

Use the built-in **ManagePullRequest** tool. Do not run `gh pr create` or `gh pr edit`
unless ManagePullRequest fails and `gh auth status` succeeds.

| Task | Tool / action |
|------|----------------|
| Open a PR | `ManagePullRequest` with `action: create_pr` |
| Update PR title/body | `ManagePullRequest` with `action: update_pr` |
| Edit labels | `EditPullRequestLabels` |

Branch naming for agent work: `cursor/<descriptive-name>-<suffix>` (suffix is assigned per agent session).

## PR description format

PR bodies must read like **feature pull requests**, not review-response documents. See
`.cursor/rules/pr-description-format.mdc`.

- Use **Summary**, **Changes**, optional **Depends on**, and **Test plan** only
- Never add sections such as "Thermos review fixes", "Fixes in this update", or "Review feedback addressed"
- After addressing review comments, fold edits into **Changes**; reply in PR comments, not as new body sections

## When `gh` is required

Some bundled skills (for example `loop-on-ci`, `fix-ci`) reference `gh pr checks`.
Before calling `gh`:

1. Confirm install finished: `.cursor/install.sh` runs on every Cloud Agent boot.
2. Resolve the binary: `command -v gh` → prefer that path; fall back to `/exec-daemon/gh`.
3. Confirm auth: `gh auth status` (or the resolved path above).
4. If auth fails, stop retrying `gh` and either use ManagePullRequest for PR work or
   verify locally with Gradle MCP (`gradle_run_tasks` `["build"]` + background/poll).

Do not install `gh` via apt, brew, or curl in agent sessions.

## CI verification in this repo

GitHub Actions runs `./gradlew build` (see `.github/workflows/main.yml`).
For agent-side verification, prefer:

1. **Gradle MCP** — `gradle_connection_status`, then `gradle_run_tasks` / `gradle_run_tests` with `background: true` + poll (see `gradle-tapi-mcp` skill)
2. Shell `./gradlew build` only when MCP is unresponsive, returns `BUILD_ALREADY_RUNNING` that cannot be cancelled, or for final CI parity after MCP passes

Use `gh pr checks` only when you need GitHub-attached check status and `gh auth status` succeeds.

## Authentication troubleshooting

| Symptom | Fix |
|---------|-----|
| `gh: command not found` | Use `/exec-daemon/gh` or re-run `.cursor/install.sh` |
| `Resource not accessible by integration` | Add `GH_TOKEN` in [Cursor Cloud Secrets](https://cursor.com/dashboard/cloud-agents); reinstall runs `gh auth login --git-protocol https --with-token` |
| ManagePullRequest fails | Push the branch first, then retry; do not fall back to `gh` unless auth works |
