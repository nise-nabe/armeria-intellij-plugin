---
name: thermo-nuclear-review
description: >-
  Token-efficient Thermo-nuclear branch audit for /thermos PR N prompts.
  Fix P0–P2 on branch, file issues for deferred P3, SHIP when clean.
  Use for branch audits — not for GitHub inline review-thread triage.
---

# Thermo-nuclear review (`/thermos PR N`)

For **GitHub review comment threads**, use `pr-review-response` instead.

Read `workflow-router` only if you have not already routed here.

## Phase 0 — Branch first (no exploration on main)

```bash
gh pr view N --json headRefName,baseRefName,title,files
git fetch origin <headRefName>
git checkout -B <headRefName> origin/<headRefName>
git diff origin/<baseRefName>...HEAD   # single diff; do not gh pr diff
```

Optional: **ManagePullRequest** `get_ci_status` once if merge-readiness matters.

## Phase 1 — Tier (controls subagent use)

| Tier | When | Thermo subagent |
|------|------|-----------------|
| **A** | Docs / scripts / ≤2 files and ≤30 net lines | **No** — use checklist below |
| **B** | Typical code PR | **Max 1** before fixes |
| **C** | Large diff or first pass found P0/P1 | **Max 2** (initial + after fixes only) |

Never run a subagent and a long manual P0–P3 audit on the same pass.

### Tier A checklist (inline, no subagent)

- [ ] User-visible strings via `message()` / bundle
- [ ] No hard-coded paths; tests reuse fixtures / `setUp()`
- [ ] Module placement matches `intellij-armeria-plugin` / `armeria-route-psi-analysis`
- [ ] Agent docs: built-in PR tools over `gh` where applicable
- [ ] Gradle: `testing { suites { … } }` — no bare `tasks.named<Test>("test")` (see `agent-workflow` rule)

## Phase 2 — Triage

| Priority | Action on branch |
|----------|------------------|
| P0–P1 | Must fix before SHIP |
| P2 | Fix in PR or block SHIP |
| P3 | Fix now **or** file one issue each — **decide before SHIP** |

Do not SHIP with “will file issues later” — create issues in the same session.

## Phase 3 — Implement + verify

1. Batch all fixes per file; one commit when possible.
2. Verify (code PRs only) — `gradle-tapi-mcp` table: **one** compile batch + **one** `gradle_run_tests` for affected classes; `ktlintCheck` before commit when `*.kt` staged.
3. Tier B/C: optional second subagent **only after** push-worthy fixes are in the tree.
4. **Do not** re-audit if `HEAD` unchanged and user only asked to file P3 issues.

## Phase 4 — Ship

```bash
git push -u origin <headRefName>
```

- **ManagePullRequest** `update_pr` when behavior changed (fold into Summary/Changes per `pr-description-format.mdc`).
- `gh issue create` for deferred P3; link issues in a PR comment (**ManagePullRequest** `post_comment`).

Report: priority table, fixed vs deferred, test command, PR link.

## Token budget

- [ ] ≤1 `gh pr view` / built-in PR metadata fetch
- [ ] ≤1 diff (`git diff`, not `gh pr diff` twice)
- [ ] Subagent count within tier cap
- [ ] No `pr-review-response` full read
- [ ] Gradle: ≤1 compile + ≤1 test batch + ktlint at commit
