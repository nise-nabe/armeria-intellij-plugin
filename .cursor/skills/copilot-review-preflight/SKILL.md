---
name: copilot-review-preflight
description: >-
  Pre-implementation and pre-PR checklist derived from recurring GitHub Copilot review
  comments on this repository. Use before opening a PR or when implementing features
  likely to trigger Copilot review (plugin UI, route collectors, agent docs/scripts).
---

# Copilot review preflight

This skill aggregates patterns from **400+** GitHub Copilot pull-request review comments
on `nise-nabe/armeria-intellij-plugin` (through August 2026, including PR #211’s 19 Spring
config-parser findings, PR #212’s 17 Spring Boot Config explorer findings, the inspection
wave in PRs #405–#460, and PR #458’s sweep of previously suppressed comments). Use it as a
final pass before requesting review.

## When to use

- Before opening or updating a PR (especially feature/fix PRs with plugin code)
- When Copilot review has flagged similar issues on prior PRs in the same area
- At the start of work in a new area — pick the specialized skill below first
- **After review comments arrive** — switch to `pr-review-response` for triage, batch fixes,
  and resolving threads. Do **not** leave Copilot threads **Suppressed** without a code fix
  or an explicit wontfix (PR #458 re-applied a dozen of these)

## Specialized skills (read the relevant one during implementation)

| Area | Skill |
|------|-------|
| **Route task first** | `workflow-router` |
| **`/thermos` branch audit** | `thermo-nuclear-review` |
| **Cloud Agent post-implementation verify** | `thermo-nuclear-review` (self-verification) |
| **Addressing PR review comments** | `pr-review-response` |
| **Issue → PR** | `issue-to-pr` |
| UI, run configs, tool windows, inspections, module placement | `intellij-armeria-plugin` |
| Route/client PSI collectors, Spring YAML/properties, virtualHost | `armeria-route-psi-analysis` |
| Gradle build/test via MCP | `gradle-tapi-mcp` |
| PR body format | `.cursor/rules/pr-description-format.mdc` |

## Quick checklist by change type

### Plugin feature code (`plugin/`, `plugin-shared/`, `plugin-wizard/`)

- [ ] User-visible strings use `message(...)` + `ArmeriaBundle.properties` (no hard-coded English, including completion docs / tooltip maps)
- [ ] No unused bundle keys; status copy lists every file type / profile variant actually scanned
- [ ] HTML tooltips escape dynamic PSI text
- [ ] Tree selection / equality uses stable route fields, not localized labels
- [ ] Background PSI uses `ReadAction.nonBlocking` + `expireWith` + `coalesceBy`
- [ ] `expireWith` is **not additive** — chaining `.expireWith(panel).expireWithPluginUnload()` replaces the first disposable (PR #417). Use `expireWhen` for a second condition, or parent one disposable under the other
- [ ] Tool-window listeners register with a `Disposable` (`toolWindow.disposable`); `selectionChanged` / first refresh only while the window is visible (PR #413/#458)
- [ ] Index-heavy paths handle `IndexNotReadyException` or defer until smart mode
- [ ] Index-backed caches use `ArmeriaRouteCacheSupport.invalidators` (PSI + project roots + dumb mode + libraries), not `PsiModificationTracker` alone (PR #410/#459)
- [ ] Kotlin-specific types are not imported in always-loaded classes without guards
- [ ] Optional `*-integration.xml` holds only extensions that need that plugin; tool windows / explorers that work without it stay in main `plugin.xml`
- [ ] Table/tree renderers reset tooltip/font every cell; use `convertRowIndexToModel`; clear model on refresh failure
- [ ] Config/`VirtualFile` text uses charset-aware VFS APIs (`LoadTextUtil`), not hard-coded UTF-8; inspections wrap `LoadTextUtil` in try/catch (PR #419)
- [ ] Hot-path filename/key checks use hoisted constants, not per-call `setOf(...)`
- [ ] Line markers / annotators: cheap owner-class or protocol guard before running collectors (do not match every `get`/`post`/`execute`) (PR #453)
- [ ] PSI initializer / reference walks use a visited-set of **resolved declarations** (not only expressions); recursion-depth guards must increment (PR #453/#458)
- [ ] Smart-pointer / PSI reads on UI restore paths happen inside a read action (PR #408)
- [ ] Run-config `main` detection uses `PsiMethodUtil`, not any `static main`
- [ ] Armeria session protocols: `H1`/`H2` are TLS; `H1C`/`H2C` are cleartext. Qualify `Server.builder()` as Armeria `Server`, not any `*.Server` (PR #457)
- [ ] `.blocking()` / `.asRestClient()` conversions apply only to `WebClient` HTTP factories (PR #408/#458)
- [ ] One test class per file matching the filename; Kotlin fixtures do not pass named args to Java methods; stubs implement `decorate()` when tests call it (PR #449/#457)
- [ ] Code and tests live in the correct module (see `intellij-armeria-plugin`)

### Inspections (Java + Kotlin + YAML/properties)

- [ ] Java inspection in `plugin.xml`; Kotlin counterpart in `kotlin-integration.xml` (same level / `enabledByDefault`); YAML/properties inspections in `yaml-integration.xml` / `properties-integration.xml`
- [ ] Treat **created** vs **mounted/registered** distinctly (e.g. `DocService.builder()` without `service` / `serviceUnder`) (PR #451/#458)
- [ ] Fluent-chain detection requires a resolved builder type (`GraphqlService` / `ServerBuilder`), not any method named `runtimeWiring` / `graphql` / `builder` (PR #446/#458)
- [ ] Quick-fixes only where the API honors the annotation (`@Blocking` on annotated routes + gRPC overrides, not `HttpService` / `DataFetcher`) (PR #446)
- [ ] Highlight the user-facing call (`service(...)`), not an earlier `decorate(...)` after `unwrapAndFollow` (PR #449)
- [ ] SPI / `META-INF/services`: `#` starts an inline comment anywhere on the line, not only at column 0 (PR #460)
- [ ] Search `gradle/libs.versions.toml` at the project root, not only the submodule content scope (PR #443)
- [ ] `@Description` does not replace `armeria-annotation-processor` for Javadoc/KDoc `@param` / `@return` / `@throws` (PR #443)
- [ ] Java **and** Kotlin fixture tests for new inspections, including class-level quick-fixes when offered (PR #446)

### YAML / properties completion (when adding contributors)

- [ ] Prefix-match the leaf lookup string, not the full dotted path
- [ ] Key-path walk includes `YAMLKeyValue` parents (nested block / flow mappings)
- [ ] No dangling `" — "` (or similar) when documentation is empty
- [ ] `.properties` value completion also treats whitespace as a delimiter (`Properties.load`) (PR #407)
- [ ] Kotlin package / insert FQCN from `KtFile`, not only `PsiClassOwner`; custom references delegate to platform refs before a same-file scan (PR #443)
- [ ] Completion/rename occurrence offsets use the **source** substring inside the literal, not the decoded string (escapes drift) (PR #405)

### Route analysis (`plugin-route-analysis/`)

- [ ] Paths and annotation values resolved via constant evaluation, not raw `expression.text`
- [ ] Kotlin interpolated / non-constant templates are not treated as folded literals (PR #458)
- [ ] `virtualHost` applies forward-only; duplicate keys include hostname
- [ ] Java and Kotlin collectors stay in parity; shared reducers updated together
- [ ] Decorator chains, annotated services, and proto/grpc routes have regression tests
- [ ] Collection uses indices/cache; no accidental full-project rescan
- [ ] Filename-driven `FilenameIndex` lookups (not `getAllFilesByExt` + filter) for known names
- [ ] “First wins” collectors sort inputs; dedupe keys include all distinguishing fields
- [ ] Built-in service classification uses a resolved FQCN / Armeria package boundary (`com.linecorp.armeria.`), and follows variable initializers when the declared type is a supertype (PR #404/#458)
- [ ] Generate HTTP: RFC 7230 tchar in `@MatchesHeader` names; still skip `name!=value`; disable for WebSocket (PR #418/#454/#458)
- [ ] Test plan uses `:plugin-route-analysis:test` or `fastTest`, not `:plugin:test`

### Spring Boot config YAML / `.properties`

- [ ] YAML via optional IntelliJ YAML PSI (`ArmeriaYamlSpringConfigReader`); gate with `PluginManagerCore.isLoaded`; keep YAML imports out of always-loaded collector entry
- [ ] Top-level `armeria` only (all YAML documents); Plain Text-typed YAML uses dummy PSI with file-level navigation fallback
- [ ] `.properties`: last-wins (including indexed keys); preserve **document order** (re-insert keys on every occurrence; do not sort parser entries by key before last-wins — relaxed-binding aliases) (PR #459); `=` / `:` delimiters; line-anchored regexes that skip `#`/`!` comments; leading whitespace is part of the key (PR #458)
- [ ] HTTP config routes use an HTTP-capable `RouteMatch` (not `NON_HTTP`); use `NON_HTTP` only for true non-HTTP protocols (DocService, Thrift, port bindings); “Generate HTTP Request” for `NON_HTTP` is enabled for **gRPC and GraphQL** only; WebSocket is unsupported
- [ ] Synthetic routes use distinct display paths (e.g. `":8080"`, not `"/"` for port bindings)
- [ ] Multi-value config (protocols, includes) reflected in emitted labels, not truncated to first
- [ ] Synthetic configurator-bean rows are counted separately from application files in summary copy (PR #407/#458)

### Agent docs and shell scripts (`.cursor/`, `.github/`, `AGENTS.md`)

- [ ] Invoke new shell scripts via `bash path/to/script.sh`, not relying on executable bit
- [ ] Installer scripts preflight required tools (`unzip`, `curl`) with clear errors
- [ ] Gradle MCP docs match the pinned server version in `install-gradle-tapi-mcp.sh`
- [ ] Gradle MCP failures: re-poll the **same** `buildId` with `includeProblems` / `includeTestDetails`; do not call `gradle_run_tests` with `taskPath: ":plugin:test"` (see `gradle-tapi-mcp`; PR #401)
- [ ] Avoid environment-specific assertions (`DISPLAY=:1`) in durable docs — phrase as sandbox-relative
- [ ] MCP config uses `stdio` transport; repo root derived from script path, not fragile `pwd`
- [ ] When editing a skill that also lives under `.github/skills/`, update **both** copies (GitHub Copilot agents load `.github/skills/…`)
- [ ] PR description is a feature summary (not a "review fixes" changelog) per `pr-description-format.mdc`

## Top recurring Copilot themes (frequency)

| Theme | Approx. hits | Primary skill |
|-------|-------------|---------------|
| Multi-module placement / wrong test task | 21+ | `intellij-armeria-plugin` |
| Run configuration / main entry detection | 19 | `intellij-armeria-plugin` |
| Async UI lifecycle (`expireWith`, dispose) | 16+ (PR #413/#417) | `intellij-armeria-plugin` |
| Hard-coded / non-localized UI strings | 16+ (PR #212 docs maps) | `intellij-armeria-plugin` |
| Inspections (created≠mounted, chain identity, Java/Kotlin parity) | 15+ (PR #419–#460) | `intellij-armeria-plugin` |
| PSI literal fallback / misleading paths | 15+ | `armeria-route-psi-analysis` |
| Annotated service / decorator parsing | 30+ | `armeria-route-psi-analysis` |
| Hand-rolled `.properties` / YAML PSI Spring config (last-wins, optional plugin gate) | 14+ (PR #211/#212/#285/#459) | `armeria-route-psi-analysis` |
| PSI initializer cycle / visited-set of resolved declarations | 8+ (PR #453/#458) | `armeria-route-psi-analysis` |
| Synthetic route emission (`RouteMatch`, display path, dedupe keys) | 6+ (PR #211) | `armeria-route-psi-analysis` |
| Index cache invalidators (roots / dumb mode / libraries) | 4+ (PR #410/#459) | `intellij-armeria-plugin` |
| FilenameIndex scan vs name-driven lookup / non-deterministic order | 3+ (PR #211) | `armeria-route-psi-analysis` |
| Armeria session protocol (`H1` TLS vs `H1C` cleartext) | 3+ (PR #457) | `intellij-armeria-plugin` |
| Optional dependency config owning core UI (`*-integration.xml`) | 2+ (PR #212) | `intellij-armeria-plugin` |
| Swing renderer reuse / view→model index / stale model on error | 3+ (PR #212) | `intellij-armeria-plugin` |
| YAML completion path / leaf prefix / empty doc tails | 3+ (PR #212) | `intellij-armeria-plugin` |
| Line-marker cheap owner-class guard | 2+ (PR #453) | `intellij-armeria-plugin` |
| Optional Kotlin plugin classloading | 11+ | `intellij-armeria-plugin` |
| Gradle MCP version/doc drift | 7+ | `gradle-tapi-mcp` |
| Bash script executable-bit assumptions | 5+ | checklist above |
| Index not ready during refresh/execute | 2+ | `intellij-armeria-plugin` |

## Commit workflow (coding agents)

When `git diff --cached --name-only -- '*.kt' '*.kts' '.editorconfig'` is non-empty, before each `git commit` run `gradle_run_tasks` with `["ktlintCheck"]` (`background: true` + poll `gradle_get_build_status` until terminal success). Fix failures with `gradle_run_tasks` `["ktlintFormat"]` (same poll) or manual edits, `git add` the changed files, re-run until clean. Wait for any in-flight MCP build to finish or cancel it (`gradle_cancel_build`) first. `ktlintFormat` is project-wide — re-stage only intended paths. Omit ktlint when the staged index contains none of those paths. Root `ktlintCheck` does not cover `build-logic/` or `settings.gradle.kts`; when all staged Kotlin is in those locations, manually review style; when a commit mixes those paths with plugin-module Kotlin, manually review the `build-logic/` and `settings.gradle.kts` portions even if `ktlintCheck` passes — see `AGENTS.md` **Commit workflow (coding agents)**.

## Verification before PR

1. Read the specialized skill for your change area.
2. **Commit implementation** on the feature branch (thermo diffs use `origin/<base>...HEAD`).
3. Run compile/tests via Gradle MCP when Kotlin/plugin code changed (see `gradle-tapi-mcp`).
   Docs-only changes may skip Gradle.
4. Run `.cursor/skills/thermo-nuclear-review/SKILL.md` self-verification (Phases 1–5) before
   the first push / `create_pr` — mandatory even when Gradle was skipped.
5. Scan the diff for `expression.text`, hard-coded `"` strings in UI code (including
   documentation maps), Kotlin imports in shared collectors, tool windows registered only
   under optional `*-integration.xml`, renderer state that is set but never cleared,
   chained `.expireWith(...).expireWithPluginUnload()`, line markers matching `get`/`post`
   without an owner-class guard, `H1` treated as cleartext HTTP, PSI walks without a
   visited-set of resolved declarations, and (for config parsers) missing comment stripping /
   `:`-in-list-scalar handling / first-match `.properties` reads / entries sorted by key
   before last-wins / `getAllFilesByExt` scans / hard-coded UTF-8 `contentsToByteArray`.
6. Write the PR body as Summary / Changes / Test plan — fold any review-driven edits into
   **Changes**, do not add "Copilot review fixes" sections. Post thermo findings tables via
   `post_comment`, not in the PR body (`pr-description-format.mdc`).

## Test plan template

```markdown
## Test plan
- [ ] `:plugin-route-analysis:fastTest` — <class or area> (if route-analysis logic changed)
- [ ] `:plugin-route-analysis:test` — <fixture test class> (if PSI fixtures added)
- [ ] `:plugin:test` — <class> (if aggregating plugin UI or inspections changed)
- [ ] `:plugin:compileKotlin` — clean compile
```

Replace task paths with the module that actually contains the test sources.
Java/Kotlin inspection pairs need both language fixture classes.
