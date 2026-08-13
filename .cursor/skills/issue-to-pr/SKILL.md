---
name: issue-to-pr
description: >-
  Token-efficient workflow to pick one GitHub issue, implement a minimal fix,
  verify with targeted Gradle MCP tests, and open a PR. Use for issue-driven tasks.
---

# Issue → PR (token-efficient)

Read `workflow-router` only if you have not already routed here.

## 1 — Pick one issue (one command)

**Oldest open** (`gh issue list --sort` is not available):

```bash
gh issue list --state open --limit 100 --json number,title,createdAt \
  | jq 'sort_by(.createdAt) | .[0]'
```

**Easy / small** — prefer labels or a tight limit; do not list and compare 30+ issues:

```bash
gh issue list --state open --label "good first issue" --limit 5 --json number,title
# or: --search "is:open no:assignee" --limit 5
```

Then: `gh issue view N` once.

## 2 — Branch

```bash
git checkout main && git pull origin main
git checkout -b cursor/issue-<N>-<short-slug>-<suffix>
```

Use plan mode only for non-trivial perf/refactors; hygiene/docs fixes go straight to code.

## 3 — Implement

- Minimal diff scoped to the issue.
- `Grep` + `Read` with `offset`/`limit` — not full-file reads unless refactoring.
- New tests only when behavior or perf changes require them.

## 4 — Verify (before commit)

Wait for `gradle_connection_status` (`connectedAny: true`).

| Step | MCP |
|------|-----|
| Compile affected module(s) | `gradle_run_tasks` e.g. `[":plugin:compileKotlin", ":plugin:compileTestKotlin"]` |
| Tests | **One** `gradle_run_tests` batch — affected class(es) only (`Grep` for callers) |
| Lint (when `*.kt` staged) | `gradle_run_tasks` `["ktlintCheck"]` — after tests, before commit |

Do not run full `build` for small fixes. Poll without `includeOutput` while `status: running`. On `status: failed`, re-poll the same `buildId` with `includeProblems: true` (compile/task failures) or `includeTestDetails: true` (test failures) before fixing — do **not** shell `./gradlew` to read errors (see `gradle-mcp.mdc` and `gradle-tapi-mcp` **Failure diagnosis**).

## 4b — Thermo self-verification (mandatory)

After Gradle verify passes, read `thermo-nuclear-review` and run **Cloud Agent self-verification**
(Phases 1–5 on `origin/main...HEAD` or the cloud task `base_branch`):

1. Deterministic scan + independent audit (Tier A/B/C per diff size).
2. Triage — fix P0–P2 on branch; file or fix every P3.
3. Closure pass on post-fix diff.
4. Re-run targeted Gradle tests only if fix commits touched production or test code.

Do not commit, push, or open the PR until thermo reports no open P0–P2 rows.

## 5 — Commit + PR

```bash
git commit -m "<type>: <summary>

Fixes #N"
git push -u origin cursor/issue-<N>-...
```

- **ManagePullRequest** `create_pr` — **open** (not draft) when Gradle verify and thermo self-verification passed.
- Body: Summary / Changes / Test plan (`pr-description-format.mdc`). Do not put thermo findings in the PR body — use `post_comment` if needed.
- `gh issue comment N --body "PR: <url>"` or link in PR body.

## Token budget

- [ ] ≤2 `gh issue` calls (list pick + view)
- [ ] ≤1 explore pass (Grep, not subagent) for simple issues
- [ ] Gradle: compile + one test batch + ktlint
- [ ] Thermo self-verification (Phases 1–5) before final push/PR
- [ ] Did not read `pr-review-response` or full `gradle-tapi-mcp`
