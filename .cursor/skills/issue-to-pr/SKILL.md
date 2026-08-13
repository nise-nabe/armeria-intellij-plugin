---
name: issue-to-pr
description: >-
  Token-efficient workflow to pick one GitHub issue, implement a minimal fix,
  verify with Gradle MCP tests, run thermo self-verification, and open a PR.
  Use for issue-driven tasks.
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

## 4 — Commit implementation

Thermo diffs use `origin/<base>...HEAD` (committed history only). Commit the implementation
before Gradle verify and thermo:

```bash
git add <paths>
git commit -m "<type>: <summary>

Fixes #N"
```

Run `ktlintCheck` before commit when `*.kt` / `*.kts` / `.editorconfig` are staged
(`AGENTS.md` **Commit workflow**).

## 5 — Verify (Gradle)

Wait for `gradle_connection_status` (`connectedAny: true`).

| Step | MCP |
|------|-----|
| Compile affected module(s) | `gradle_run_tasks` e.g. `[":plugin:compileKotlin", ":plugin:compileTestKotlin"]` |
| Tests | **One** `gradle_run_tests` batch — affected class(es) only (`Grep` for callers) |

Skip Gradle when the change is docs-only (`.cursor/`, `AGENTS.md`, scripts with no Kotlin).
Do not run full `build` for small fixes. Poll without `includeOutput` while `status: running`.
On `status: failed`, re-poll the same `buildId` with `includeProblems: true` (compile/task
failures) or `includeTestDetails: true` (test failures) before fixing — do **not** shell
`./gradlew` to read errors (see `gradle-mcp.mdc` and `gradle-tapi-mcp` **Failure diagnosis**).

## 6 — Thermo self-verification (mandatory)

After §4 commit (and §5 Gradle when applicable), read `thermo-nuclear-review` and run
**Cloud Agent self-verification** (Phases 1–5 on `origin/main...HEAD` or the cloud task
`base_branch`):

1. Deterministic scan + independent audit (Tier A/B/C per diff size).
2. Triage — fix P0–P2 on branch; file or fix every P3.
3. Closure pass on post-fix diff.
4. Re-run targeted Gradle tests only if fix commits touched production or test code.
5. `ktlintCheck` before each thermo fix commit when Kotlin is staged.

Do **not** push or open the PR until thermo reports no open P0–P2 rows. Thermo fix commits
are expected.

## 7 — Push + PR

```bash
git push -u origin cursor/issue-<N>-...
```

- **ManagePullRequest** `create_pr` — **open** (not draft) when Gradle verify (if run) and
  thermo self-verification passed.
- Body: Summary / Changes / Test plan (`pr-description-format.mdc`). Do not put thermo
  findings in the PR body — use `post_comment` if needed.
- `gh issue comment N --body "PR: <url>"` or link in PR body.

## Token budget

- [ ] ≤2 `gh issue` calls (list pick + view)
- [ ] ≤1 explore pass (Grep, not subagent) for simple issues
- [ ] Gradle: compile + one test batch (skip when docs-only)
- [ ] Thermo self-verification (Phases 1–5) before first push/PR — Tier A skips audit subagent;
  Tier B/C may add one audit subagent; `/thermos` Tier C may add one closure subagent
- [ ] Did not read `pr-review-response` or full `gradle-tapi-mcp`
