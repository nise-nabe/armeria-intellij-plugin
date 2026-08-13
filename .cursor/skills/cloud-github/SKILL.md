---
name: cloud-github
description: >-
  GitHub and pull-request workflows for Cursor Cloud Agents in this repo.
  Prefer built-in ManagePullRequest and EditPullRequestLabels over gh CLI;
  use gh only for operations with no built-in tool (review-thread fetch, PR metadata,
  releases).
---

# Cloud GitHub operations

Cursor Cloud Agents in this repository must not fail because `gh` is missing from PATH.
`.cursor/install.sh` symlinks `/exec-daemon/gh` into `~/.local/bin/gh` and, when
`/usr/local/bin` is writable, into `/usr/local/bin/gh`. When `gh auth status` is unauthenticated,
it logs in with `GH_TOKEN` or `GITHUB_TOKEN` if set.

## PR operations (preferred — built-in tools)

Use the built-in **ManagePullRequest** and **EditPullRequestLabels** tools. Do **not** run
`gh pr create`, `gh pr edit`, `gh pr comment`, `gh pr checks`, or `gh pr close` unless the
built-in tool fails and `gh auth status` succeeds.

| Task | Tool / action |
|------|----------------|
| Open a PR | `ManagePullRequest` with `action: create_pr` |
| Update PR title/body | `ManagePullRequest` with `action: update_pr` |
| Post a top-level PR comment | `ManagePullRequest` with `action: post_comment` |
| Reply to a review comment | `ManagePullRequest` with `action: post_comment`, `in_reply_to: <databaseId>` |
| Comment on a file/line in the diff | `ManagePullRequest` with `action: post_comment`, `path` + `line` (optional `start_line`, `side`) |
| Resolve a review thread | `ManagePullRequest` with `action: resolve_comment`, `comment_id: <databaseId>` |
| PR check / CI status | `ManagePullRequest` with `action: get_ci_status` |
| Open or close a PR | `ManagePullRequest` with `action: set_pr_status`, `status: open` or `closed` |
| Edit labels | `EditPullRequestLabels` with `add_labels` / `remove_labels` |

Branch naming for agent work: `cursor/<descriptive-name>-<suffix>` (suffix is assigned per agent session).

When a bundled skill (for example `loop-on-ci`, `fix-ci`, `get-pr-comments`, `new-branch-and-pr`,
`review-and-ship`, or any other skill that references `gh pr …`) instructs `gh pr create`,
`gh pr checks`, or similar, use the matching built-in tool above instead in Cursor Cloud.

## PR description format

PR bodies must read like **feature pull requests**, not review-response documents. See
`.cursor/rules/pr-description-format.mdc`.

- Use **Summary**, **Changes**, optional **Depends on**, and **Test plan** only
- Never add sections such as "Thermos review fixes", "Fixes in this update", or "Review feedback addressed"
- After addressing review comments, fold edits into **Changes**; reply in PR comments via `post_comment`, not as new body sections

## When `gh` is still required

There is **no** built-in tool for these operations today:

| Task | gh command |
|------|------------|
| Fetch review threads / inline comment metadata | `gh api graphql` (see `pr-review-response` skill) |
| Fetch PR branch metadata (no review threads) | `gh pr view --json headRefName,baseRefName,title` |
| CI failure logs (run/job ID from check URLs) | `gh run view <run-id> --log-failed` or `gh run view --job <job-id> --log-failed` |
| Create a GitHub Release with assets | `gh release create` (see `release` skill) |

Before calling `gh` for the above:

1. Confirm install finished: `.cursor/install.sh` runs on every Cloud Agent boot.
2. Resolve the binary: `command -v gh` → prefer that path; fall back to `/exec-daemon/gh`.
3. Confirm auth: `gh auth status` (or the resolved path above).
4. If auth fails, stop retrying `gh`. Use built-in PR tools for create/update/comment/resolve/CI;
   verify locally with Gradle MCP (`gradle_run_tasks` `["build"]` + background/poll).

Do not install `gh` via apt, brew, or curl in agent sessions.

## CI verification in this repo

GitHub Actions runs `./gradlew build` (see `.github/workflows/main.yml`).
For agent-side verification, prefer:

1. **Commit implementation** — thermo diffs use committed history (`origin/<base>...HEAD`).
2. **Gradle MCP** — when Kotlin/plugin code changed: `gradle_connection_status`, then
   `gradle_run_tasks` / `gradle_run_tests` with `background: true` + poll (see `gradle-tapi-mcp`
   skill). Docs-only tasks may skip Gradle.
3. **Thermo self-verification** — mandatory before the final push / `create_pr` even when Gradle
   was skipped (Tier A for docs-only). Run `.cursor/skills/thermo-nuclear-review/SKILL.md`
   Phases 1–5 on the feature branch (see `agent-workflow.mdc` **Cloud Agent self-verification**)
4. Shell `./gradlew build` only when MCP is unresponsive, returns `BUILD_ALREADY_RUNNING` that cannot be cancelled, or for final CI parity after MCP passes

For GitHub-attached check status on a PR, use **ManagePullRequest** `get_ci_status` (not `gh pr checks`).
Fall back to `gh pr checks` only when `get_ci_status` fails and `gh auth status` succeeds.
When a check fails and you need log output to fix it, follow the failing check's URL from
`get_ci_status` (or `gh pr checks` fallback) first. To fetch logs via CLI, use
`gh run view <run-id> --log-failed` or `gh run view --job <job-id> --log-failed` with the run or
job ID from those URLs (after `gh auth status` succeeds).

## Authentication troubleshooting

| Symptom | Fix |
|---------|-----|
| `gh: command not found` | Use `/exec-daemon/gh` or re-run `.cursor/install.sh` |
| `Resource not accessible by integration` | Add `GH_TOKEN` in [Cursor Cloud Secrets](https://cursor.com/dashboard/cloud-agents); reinstall runs `gh auth login --git-protocol https --with-token` |
| ManagePullRequest fails | Push the branch first, then retry; do not fall back to `gh` unless auth works |
