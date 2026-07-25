---
name: workflow-router
description: >-
  Route Cloud Agent tasks to the correct on-demand skill. Read this first when the
  user prompt matches /thermos, PR review comments, issue fixes, or docs-only edits.
  Prevents loading the wrong skill and wasting tokens.
---

# Workflow router (read first)

Pick **one** path below. Do **not** read unrelated skills in full.

| User intent | Read only | Do not read |
|-------------|-----------|-------------|
| `/thermos PR N`, Thermo branch audit | `thermo-nuclear-review` | `pr-review-response` |
| Address PR review comments / resolve threads | `pr-review-response` | `thermo-nuclear-review` |
| Fix an issue → open PR | `issue-to-pr` | `pr-review-response` |
| Docs / `.cursor/` / `AGENTS.md` only | `cloud-github` + target files via Grep | `gradle-tapi-mcp` (full) |
| Plugin / route code (implementation) | Area skill from table below | Full `AGENTS.md` |

## Area skills (implementation only)

| Area | Skill |
|------|-------|
| Plugin UI, run configs, inspections | `intellij-armeria-plugin` |
| Route collectors, PSI, Spring config | `armeria-route-psi-analysis` |
| Gradle verify (after you know the task) | `gradle-tapi-mcp` — verification table only |
| Pre-PR Copilot patterns | `copilot-review-preflight` — checklist for changed area only |

## Session reuse (token save)

- Same PR or issue: **resume an IDLE Cloud Agent** (user may pass `bc-…` URL) instead of starting a new session.
- If `HEAD` is unchanged since last SHIP and the user only asks to file P3 issues, create issues — **do not** re-run Thermo or tests.

## Fetch once

| Need | One call |
|------|----------|
| PR branch + files | `gh pr view N --json headRefName,baseRefName,title,files` or **ManagePullRequest** |
| Diff after checkout | `git diff origin/<base>...HEAD` — not `gh pr diff` |
| Review threads | GraphQL once (`pr-review-response`); if empty, do not re-fetch |
