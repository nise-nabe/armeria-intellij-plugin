---
name: semantic-comments
description: >-
  Semantic comments and KDoc for Kotlin: write specification-style comments that capture
  observable behavior, constraints, and rationale—not syntax restatement. Use when adding
  or editing comments, KDoc, PSI collectors, route semantics, virtualHost scoping, config
  parsers, or implementing non-obvious IntelliJ Platform logic in this repository.
---

# Semantic comments

Read `.github/instructions/semantic-comments.instructions.md` when this skill applies.

## Quick rules

1. **Semantic** — behavior, contracts, limitations, and non-obvious *why*.
2. **Not trivial** — skip comments that duplicate the code or UI strings (`message()` + bundle).
3. **Comment-first** — for complex new collectors/algorithms, outline the spec in comments, then code.
4. **English** — match existing production Kotlin style.
5. **KDoc on public API** — classes, interfaces, and public functions.

## Related skills

| Area | Skill |
|------|-------|
| Route PSI / collectors | `armeria-route-psi-analysis` |
| Plugin UI strings | `intellij-armeria-plugin` |
| PR review thread replies | `pr-review-response` (GitHub comments, not source comments) |
