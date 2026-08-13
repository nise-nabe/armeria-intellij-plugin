---
applyTo: "**/*.kt,**/*.kts"
description: >-
  Semantic comments and KDoc for Kotlin: specification-style comments that capture
  observable behavior, constraints, and rationale—not syntax restatement. Use when
  adding or editing comments, KDoc, PSI collectors, route semantics, or non-obvious logic.
---

# Semantic comments (Kotlin)

Write **semantic comments**: they document what the code means for callers and maintainers
(observable behavior, invariants, supported shapes, edge cases, and *why* when non-obvious).
They are not narration of syntax.

## When to comment

- **Public API** — KDoc on public classes, interfaces, and functions.
- **Non-obvious logic** — PSI traversal, route semantics, virtualHost scoping, config parsing,
  decorator/timeout resolution, duplicate-index keys, Kotlin vs Java PSI differences.
- **Comment-first for complex work** — for new collectors or multi-step algorithms, sketch the
  specification in comments (supported inputs, outputs, and limitations), then implement to match.
- **Test intent** — when a fixture or assertion encodes a subtle regression, one line on the
  behavior under test is enough.

## When not to comment

- Self-explanatory code (names and structure already convey intent).
- **User-visible UI text** — use `message(...)` and `ArmeriaBundle.properties`, not comments.
- Restating the code (`// increment i`, `// return result`).
- Changelog or PR narrative in source files.

## Style (match this repository)

- Prefer block KDoc for types and public members; use short inline comments only at decision points.
- State **constraints and limitations** explicitly (e.g. literal paths only, last-wins keys,
  comment lines skipped in properties regex).
- Reference related types with KDoc links when cross-module (`[RouteCollectContext]`).
- Keep comments in **English** (same as production Kotlin in this repo).

## Examples in this codebase

- `RouteContributor.kt` — SPI role and production wiring.
- `ArmeriaScalaTextSupport.kt` — supported route shapes and parsing limits.
- `SpringArmeriaConfigSemantics.kt` — shared config semantics object.

## After editing comments

- If staged Kotlin changed, run `ktlintCheck` before commit (see `AGENTS.md` commit workflow).
