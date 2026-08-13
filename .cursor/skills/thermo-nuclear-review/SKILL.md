---
name: thermo-nuclear-review
description: >-
  One-shot Thermo-nuclear branch audit for /thermos PR N and Cloud Agent post-implementation
  self-verification. Independent audit pass, fix P0–P2, file P3 issues, closure verification,
  SHIP — no second session needed. Not for GitHub inline review-thread triage.
---

# Thermo-nuclear review — one-shot complete

For **GitHub review comment threads**, use `pr-review-response` instead.

**Goal:** finish in **one user invocation** with confidence comparable to a fresh second
session. Use **independent passes** inside the session — not IDLE resume across sessions.

Read `workflow-router` only if you have not already routed here.

## Entry modes

| Mode | When | Phase 0 base |
|------|------|--------------|
| **`/thermos PR N`** | User or automation requests branch audit on an existing PR | `baseRefName` from PR metadata |
| **Cloud Agent self-verification** | After implementation + Gradle verify, **before** final push / `create_pr` | `main` (or `base_branch` from cloud task instructions) |

Both modes run the **same Phases 1–6**. Self-verification is mandatory for Cloud Agents even
when Gradle compile/tests already passed — the closure pass is the built-in second look.

## Phase 0 — Setup (once)

### `/thermos PR N`

```bash
gh pr view N --json headRefName,baseRefName,title,files
git fetch origin <headRefName>
git checkout -B <headRefName> origin/<headRefName>
git diff origin/<baseRefName>...HEAD > /tmp/pr-diff.txt
```

### Cloud Agent self-verification

Agent is already on the feature branch after implementation:

```bash
git fetch origin main   # or the cloud task base_branch
BASE=<baseRefName>      # usually main
git diff origin/$BASE...HEAD > /tmp/pr-diff.txt
```

Use `git diff --name-only origin/$BASE...HEAD` for the changed-file list when no PR exists yet.

- **ManagePullRequest** `get_ci_status` once when a PR already exists and merge-readiness matters.
- Record `BASE_SHA=$(git merge-base origin/<baseRefName> HEAD)` and `START_HEAD=$(git rev-parse HEAD)`.

Do not explore on `main`. Do not pass prior session conclusions into later phases.

## Phase 1 — Deterministic scan (objective, no judgment)

Map `files` from PR metadata to **one** `copilot-review-preflight` subsection. Run only
checklist items verifiable by Grep / targeted `Read` on changed paths — not a subjective
P0–P3 essay in the parent agent.

| Changed area | Preflight section |
|--------------|-------------------|
| `plugin/`, `plugin-shared/`, `plugin-wizard/` | Plugin feature code |
| `plugin-route-*/` collectors | Route analysis |
| `plugin-route-spring/` config | Spring Boot config |
| `.cursor/`, `AGENTS.md`, scripts | Agent docs and shell scripts |

Also run the Tier A Gradle/docs bullets when applicable (bundle keys, `testing { suites }`, etc.).

Output a **findings table** (id, source, path, priority, action). Sources: `deterministic` only.

## Phase 2 — Independent audit pass (fresh judgment)

Tier controls cost; each tier still completes in one invocation.

| Tier | When | Subagent |
|------|------|----------|
| **A** | Docs / scripts / ≤2 files and ≤30 net lines | **Skip** — Phase 1 table is the audit |
| **B** | Typical code PR | **One** audit subagent |
| **C** | Large diff or many files | **One** audit subagent + **one** post-fix closure subagent (Phase 5 only) |

### Subagent input (mandatory — simulates a new session)

Pass **only**:

- `git diff origin/<base>...HEAD` (or `/tmp/pr-diff.txt`)
- PR title and changed file list
- Thermo rubric / priority definitions
- Instruction: *return a findings table; do not assume any prior SHIP or triage*

**Do not pass:** parent agent analysis, earlier findings table, chat history summary, or
"we already checked X".

Parent agent: **no** long manual P0–P3 audit in parallel with the subagent.

Merge subagent rows into the findings table (`source: audit`). Deduplicate by path + concern.

## Phase 3 — Triage gate (block until clear)

| Priority | Rule |
|----------|------|
| P0–P1 | Fix on branch before SHIP |
| P2 | Fix on branch or block SHIP |
| P3 | Fix now **or** `gh issue create` — **every P3 row resolved before Phase 4** |

No SHIP with open P3 rows marked "later".

## Phase 4 — Fix + verify

1. Batch fixes per file; prefer one commit.
2. Code PRs: **one** compile batch + **one** `gradle_run_tests` for affected classes.
3. `ktlintCheck` before commit when `*.kt` / `*.kts` staged.
4. Update findings table: `fixed` / `issue #NNN` / `wontfix` (with reason).

## Phase 5 — Closure pass (replaces a second session)

Run **after** fixes are committed (before push). Mandatory for Tier B/C; Tier A when code changed.

1. `git diff origin/<baseRefName>...HEAD` — post-fix diff only.
2. **Findings closure:** every row from Phases 1–2 has a terminal status.
3. **Deterministic re-scan:** re-run Phase 1 checklist on **newly changed hunks** only.
4. **Tests:** rerun only if fix commit touched production or test code (`git diff START_HEAD..HEAD --name-only`).
5. Tier C only: **one** closure subagent with **post-fix diff only** — same blind-input rules as Phase 2.
   - If it reports new P0–P1 → return to Phase 4 once; do not loop.

**SHIP blocked** if any P0–P2 row is open or closure finds new P0–P1.

## Phase 6 — Ship

```bash
git push -u origin <headRefName>
```

- **ManagePullRequest** `update_pr` when behavior changed (fold into Summary/Changes per `pr-description-format.mdc`).
- **ManagePullRequest** `post_comment` — findings table (fixed / issue / skipped) + test commands.
  Post the findings table in a **PR comment**, not in the PR body (`pr-description-format.mdc`).
- Link created P3 issues.

User summary: findings table, SHIP verdict, PR link. State that closure pass ran (no second session required).

## Cloud Agent ordering (with implementation workflows)

Typical sequence on a feature branch:

1. Implement minimal diff.
2. **Gradle verify** — compile + targeted tests (`gradle-tapi-mcp`; `issue-to-pr` §4).
3. **Thermo self-verification** — Phases 1–5 on `origin/<base>...HEAD`; fix P0–P2 on branch.
4. `ktlintCheck` when `*.kt` / `*.kts` staged; commit fix batches.
5. **Ship** — Phase 6: push, `create_pr` / `update_pr`, optional `post_comment` with findings table.

Do not open or finalize the PR until Phases 1–5 complete with no open P0–P2 rows.

## `/thermos` and session resume

| Situation | Action |
|-----------|--------|
| New `/thermos PR N` | Run **Phases 0–6** fully; ignore prior session SHIP |
| Resume + "続き" / push only | Skip re-audit; complete unfinished push/issue links |
| Resume + same `/thermos` again | **Full Phases 0–6** — treat as new audit at current `HEAD` |

Do not recommend a second Cloud session for confidence; the closure pass is the built-in second look.

## Token budget

- [ ] ≤1 PR metadata fetch; ≤2 diffs (initial + post-fix)
- [ ] Subagent count within tier cap; blind input only
- [ ] No `pr-review-response` full read
- [ ] Gradle: ≤2 compile/test rounds (pre-fix + post-fix only if code changed)
- [ ] No parent + subagent duplicate audit on the same diff
