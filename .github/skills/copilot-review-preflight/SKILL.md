---
name: copilot-review-preflight
description: >-
  Pre-implementation and pre-PR checklist derived from recurring GitHub Copilot review
  comments on this repository. Use before opening a PR or when implementing features
  likely to trigger Copilot review (plugin UI, route collectors, agent docs/scripts).
---

# Copilot review preflight

This skill aggregates patterns from **337+** GitHub Copilot pull-request review comments
on `nise-nabe/armeria-intellij-plugin` (through July 2026, including PR #211’s 19 Spring
config-parser findings). Use it as a final pass before requesting review.

## When to use

- Before opening or updating a PR (especially feature/fix PRs with plugin code)
- When Copilot review has flagged similar issues on prior PRs in the same area
- At the start of work in a new area — pick the specialized skill below first
- **After review comments arrive** — switch to `pr-review-response` for triage, batch fixes, and resolving threads efficiently

## Specialized skills (read the relevant one during implementation)

| Area | Skill |
|------|-------|
| **Addressing PR review comments** | `pr-review-response` |
| UI, run configs, tool windows, inspections, module placement | `intellij-armeria-plugin` |
| Route/client PSI collectors, Spring YAML/properties, virtualHost | `armeria-route-psi-analysis` |
| Gradle build/test via MCP | `gradle-tapi-mcp` |
| PR body format | `.cursor/rules/pr-description-format.mdc` |

## Quick checklist by change type

### Plugin feature code (`plugin/`, `plugin-shared/`, `plugin-wizard/`)

- [ ] User-visible strings use `message(...)` + `ArmeriaBundle.properties` (no hard-coded English)
- [ ] HTML tooltips escape dynamic PSI text
- [ ] Tree selection / equality uses stable route fields, not localized labels
- [ ] Background PSI uses `ReadAction.nonBlocking` + `expireWith` + `coalesceBy`
- [ ] Index-heavy paths handle `IndexNotReadyException` or defer until smart mode
- [ ] Kotlin-specific types are not imported in always-loaded classes without guards
- [ ] Run-config `main` detection uses `PsiMethodUtil`, not any `static main`
- [ ] Code and tests live in the correct module (see `intellij-armeria-plugin`)

### Route analysis (`plugin-route-analysis/`)

- [ ] Paths and annotation values resolved via constant evaluation, not raw `expression.text`
- [ ] `virtualHost` applies forward-only; duplicate keys include hostname
- [ ] Java and Kotlin collectors stay in parity; shared reducers updated together
- [ ] Decorator chains, annotated services, and proto/grpc routes have regression tests
- [ ] Collection uses indices/cache; no accidental full-project rescan
- [ ] Filename-driven `FilenameIndex` lookups (not `getAllFilesByExt` + filter) for known names
- [ ] “First wins” collectors sort inputs; dedupe keys include all distinguishing fields
- [ ] Test plan uses `:plugin-route-analysis:test` or `fastTest`, not `:plugin:test`

### Hand-rolled Spring YAML / `.properties` parsers

- [ ] Unquoted YAML scalars/lists strip trailing `# …`; comment-only values are empty
- [ ] Nested YAML keys matched only at parent indentation (not first-anywhere)
- [ ] `.properties`: last-wins (including indexed keys); `=` and `:` delimiters; line-anchored regexes that skip `#`/`!` comments
- [ ] HTTP config routes use an HTTP-capable `RouteMatch` (not `NON_HTTP`); use `NON_HTTP` only for true non-HTTP protocols (DocService, Thrift, port bindings); note “Generate HTTP Request” for `NON_HTTP` is enabled **only for gRPC**
- [ ] Synthetic routes use distinct display paths (e.g. `":8080"`, not `"/"` for port bindings)
- [ ] Multi-value config (protocols, includes) reflected in emitted labels, not truncated to first

### Agent docs and shell scripts (`.cursor/`, `.github/`, `AGENTS.md`)

- [ ] Invoke new shell scripts via `bash path/to/script.sh`, not relying on executable bit
- [ ] Installer scripts preflight required tools (`unzip`, `curl`) with clear errors
- [ ] Gradle MCP docs match the pinned server version in `install-gradle-tapi-mcp.sh`
- [ ] Avoid environment-specific assertions (`DISPLAY=:1`) in durable docs — phrase as sandbox-relative
- [ ] MCP config uses `stdio` transport; repo root derived from script path, not fragile `pwd`
- [ ] When editing a skill that also lives under `.github/skills/`, update **both** copies (GitHub Copilot agents load `.github/skills/…`)
- [ ] PR description is a feature summary (not a "review fixes" changelog) per `pr-description-format.mdc`

## Top recurring Copilot themes (frequency)

| Theme | Approx. hits | Primary skill |
|-------|-------------|---------------|
| Multi-module placement / wrong test task | 21+ | `intellij-armeria-plugin` |
| Run configuration / main entry detection | 19 | `intellij-armeria-plugin` |
| Async UI lifecycle (`expireWith`, dispose) | 16 | `intellij-armeria-plugin` |
| Hard-coded / non-localized UI strings | 16 | `intellij-armeria-plugin` |
| PSI literal fallback / misleading paths | 15+ | `armeria-route-psi-analysis` |
| Annotated service / decorator parsing | 30+ | `armeria-route-psi-analysis` |
| Hand-rolled YAML/properties parsing (comments, last-wins, delimiters) | 11+ (PR #211) | `armeria-route-psi-analysis` |
| Synthetic route emission (`RouteMatch`, display path, dedupe keys) | 6+ (PR #211) | `armeria-route-psi-analysis` |
| FilenameIndex scan vs name-driven lookup / non-deterministic order | 3+ (PR #211) | `armeria-route-psi-analysis` |
| Optional Kotlin plugin classloading | 11+ | `intellij-armeria-plugin` |
| Gradle MCP version/doc drift | 7+ | `gradle-tapi-mcp` |
| Bash script executable-bit assumptions | 5+ | checklist above |
| Index not ready during refresh/execute | 2+ | `intellij-armeria-plugin` |

## Verification before PR

1. Read the specialized skill for your change area.
2. Run compile/tests via Gradle MCP with the correct module `taskPath` (see `gradle-tapi-mcp`).
3. Scan the diff for `expression.text`, hard-coded `"` strings in UI code, Kotlin imports
   in shared collectors, and (for config parsers) missing comment stripping / first-match
   `.properties` reads / `getAllFilesByExt` scans.
4. Write the PR body as Summary / Changes / Test plan — fold any review-driven edits into
   **Changes**, do not add "Copilot review fixes" sections.

## Test plan template

```markdown
## Test plan
- [ ] `:plugin-route-analysis:fastTest` — <class or area> (if route-analysis logic changed)
- [ ] `:plugin-route-analysis:test` — <fixture test class> (if PSI fixtures added)
- [ ] `:plugin:test` — <class> (if aggregating plugin UI changed)
- [ ] `:plugin:compileKotlin` — clean compile
```

Replace task paths with the module that actually contains the test sources.
