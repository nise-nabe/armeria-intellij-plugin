---
name: pr-review-response
description: >-
  Efficient workflow for triaging, fixing, and resolving GitHub PR review comments
  (Copilot, Thermos, human reviewers). Optimized for token use and agent round-trips.
  Use when asked to address PR review comments, resolve review threads, or fix
  findings from a specific pull request number.
---

# PR review response (token-efficient)

Workflow for **triage → batch fix → verify once → resolve threads**, derived from
agent sessions on this repository (e.g. PR #208, ~45 tool rounds without this skill).

Read **`cloud-github`** for PR tools and **`copilot-review-preflight`** for recurring
fix patterns. Read **`gradle-tapi-mcp`** only for the verification subsection below —
do not read the full skill unless Gradle MCP fails.

## When to use

- User asks to address / fix / resolve comments on PR *N*
- After Copilot or Thermos review on an open PR
- Before marking review threads resolved

## Phase 1 — Fetch comments (minimal payload)

Before any `gh` call in Cursor Cloud (see `.cursor/rules/cloud-github.mdc`):

1. Resolve: `command -v gh` or `/exec-daemon/gh`
2. Verify: `gh auth status`
3. If either step fails, stop — do not install `gh` in agent sessions. Use
   **ManagePullRequest** for PR create/update/resolve; retry GraphQL only after auth works.

**Do not** call `gh api repos/.../pulls/{n}/comments` — the REST endpoint always
includes `diff_hunk` per comment (~50 KB for a typical Copilot review) and there is
no query parameter to omit it.

### Preferred: GraphQL (resolved state + metadata only)

The query below caps at **100 review threads** and **5 comments per thread** (`first` limits).
That is enough for typical Copilot/Thermos reviews; for larger PRs, paginate with
`pageInfo { hasNextPage endCursor }` on `reviewThreads` (and on `comments` inside a thread
when a thread has more than five replies) instead of raising `first` blindly — token cost
grows with every extra node.

```bash
gh api graphql -f query='
{
  repository(owner: "OWNER", name: "REPO") {
    pullRequest(number: N) {
      title
      headRefName
      baseRefName
      reviewThreads(first: 100) {
        pageInfo { hasNextPage endCursor }
        nodes {
          id
          isResolved
          comments(first: 5) {
            pageInfo { hasNextPage endCursor }
            nodes {
              databaseId
              body
              path
              line
              originalLine
              startLine
              originalStartLine
              author { login }
            }
          }
        }
      }
    }
  }
}'
```

Extract:

| Field | Use |
|-------|-----|
| `databaseId` | `ManagePullRequest` `resolve_comment` `comment_id` |
| `isResolved` | Skip already resolved |
| `body` + `path` + line fields | Triage and locate code — prefer `line`; when `line` is null (outdated thread), use `originalLine`; for multi-line comments use `startLine` / `originalStartLine` |
| `headRefName` | Checkout branch |

### Fallback: PR metadata only (not review threads)

When you need branch names before running GraphQL (or to recover from a GraphQL failure),
fetch PR metadata separately — **`gh pr view` does not return review threads or comment bodies**:

```bash
gh pr view N --json headRefName,baseRefName,title
```

Review comments still require the GraphQL query above. There is no lightweight REST
alternative: `gh pr view N --comments` shows only issue-style conversation comments, not
inline code review threads (and `gh pr view --json reviews` omits line comments too). Avoid
`gh api repos/.../pulls/{n}/comments` (always includes `diff_hunk`). Retry or paginate
GraphQL instead.

### Checkout once

```bash
git fetch origin <headRefName>
git checkout -B <headRefName> origin/<headRefName>
```

Do not explore the repo on `main` if the PR branch exists.

## Phase 2 — Triage (before reading code)

For each **unresolved** thread, record:

| Thread | Valid? | Priority | Action |
|--------|--------|----------|--------|
| … | yes/no | P0–P3 | fix / skip + reply |

**Priority guide**

| Priority | Examples |
|----------|----------|
| **P0** | Crashes (`NoClassDefFoundError`, `IndexNotReadyException`), wrong results in production paths |
| **P1** | Real bugs, security, broken navigation/resolution for common cases |
| **P2** | Performance, test stability, redundant work, misleading API usage |
| **P3** | Style, dead code, unused variables |

Skip invalid comments with a brief PR reply; do not implement speculative fixes.

**Batch by file** — e.g. all `ArmeriaRouteNavigationSupport.kt` comments in one edit pass.

## Phase 3 — Read code (targeted)

| Do | Don't |
|----|-------|
| `Grep` for symbol / line from comment | Read entire large files |
| `Read` with `offset`/`limit` around `line` (or `originalLine` when outdated) | Re-read files already in context |
| Read **one** specialized skill for the area | Read `gradle-tapi-mcp` in full |
| Follow `copilot-review-preflight` checklist | Re-fetch PR comments |

For optional Kotlin plugin issues, follow the **`ArmeriaClientCollector` / `ArmeriaKotlinClientCollector`** split pattern (guard with `PluginManagerCore.isLoaded`, no `Kt*` imports in always-loaded classes).

## Phase 4 — Implement (single pass)

1. Apply all accepted fixes per file before any Gradle run.
2. Keep diffs minimal — one concern per hunk where possible.
3. Add/adjust tests only when the comment is about missing coverage or you fixed a bug.
4. For test fixtures: **reuse `setUp()` stubs** — do not duplicate Java FQCN annotations as Kotlin `annotation class` in the same fixture.

Commit once before verification:

```bash
git add <paths>
git commit -m "fix: address PR <N> review comments"
```

## Phase 5 — Verify (one compile + one test run)

Follow **`gradle-tapi-mcp`** constraints:

1. `gradle_connection_status` — stop if not connected.
2. **One** `gradle_run_tasks` with `[":<module>:compileKotlin", ":<module>:compileTestKotlin"]`, `background: true` on cold start.
3. Poll `gradle_get_build_status` until terminal — **do not** set `includeOutput: true` while `status: running` (reduces duplicate stdout in agent context). Enable output only on failure.
4. **One** `gradle_run_tests` batching all affected test classes/methods in a single call.
5. On failure, rerun **only** the failing method(s) — not the full class suite.

| Changed code in | Compile | Tests |
|-----------------|---------|-------|
| `plugin/` | `:plugin:compileKotlin` | `:plugin:test` |
| `plugin-route-analysis/` | `:plugin-route-analysis:compileKotlin` | `:plugin-route-analysis:fastTest` or `:test` |

Do **not** run `build` for review-fix verification unless the PR touched build logic or CI parity is explicitly required.

## Phase 6 — Push and resolve

```bash
git push -u origin <headRefName>
```

Resolve threads with **ManagePullRequest** `resolve_comment` — pass each `databaseId` from GraphQL. Resolve only after the fix is pushed.

```
action: resolve_comment
branch_name: <headRefName>
comment_id: <databaseId>
```

Update PR body via `update_pr` only when behavior changed materially — fold fixes into **Changes**, not a "review fixes" section (see `.cursor/rules/pr-description-format.mdc`).

## Phase 7 — User summary

Report in the user’s language:

1. Per-comment validity and priority (table)
2. What was fixed vs skipped
3. Test command and result
4. Link to PR

## Token budget checklist

- [ ] `gh` preflight (`command -v gh`, `gh auth status`) before GraphQL
- [ ] GraphQL used instead of REST review comments API
- [ ] ≤ 1 branch checkout
- [ ] Files read with offset/limit or Grep, not full-file unless refactoring
- [ ] Gradle: ≤ 1 compile + ≤ 2 test MCP calls (full batch + optional single-method retry)
- [ ] No `sleep` polling loops longer than needed — poll every 15–30s, no `includeOutput` until failed
- [ ] `resolve_comment` batched in one agent turn (parallel tool calls)
- [ ] Did not re-read `gradle-tapi-mcp` or `AGENTS.md` in full

## Anti-patterns (observed in high-token sessions)

| Anti-pattern | Cost | Alternative |
|--------------|------|-------------|
| REST `/pulls/comments` with `diff_hunk` | ~12k tok | GraphQL without hunks |
| 45+ tool rounds (fix → test → fix → test) | ~30k tok cumulative | Batch fixes, one verify |
| Full file Write after Read | ~6k tok per file | StrReplace hunks |
| `gradle_get_build_status` + `includeOutput: true` every 30s while running | Growing stdout each poll | Poll without output until terminal |
| Reading 16 KB gradle skill for a compile+test | ~4k tok | Use table above |
| Six sequential `resolve_comment` turns | 6 round-trips | Parallel in one message |

## Related skills

| Need | Skill |
|------|-------|
| PR create/update body | `cloud-github` + `pr-description-format.mdc` |
| Copilot recurring patterns | `copilot-review-preflight` |
| Plugin / PSI conventions | `intellij-armeria-plugin` |
| Route collectors | `armeria-route-psi-analysis` |
| Gradle MCP details / failures | `gradle-tapi-mcp` |
