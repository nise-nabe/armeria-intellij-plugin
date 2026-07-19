---
name: armeria-route-psi-analysis
description: >-
  PSI and route-analysis implementation patterns for the plugin-route-* modules. Use when
  editing ArmeriaRouteCollector, decorator/registration collectors, duplicate detection,
  virtualHost scoping, annotated-service parsing, Spring YAML/properties config collectors,
  or related regression tests.
---

# Armeria route PSI analysis

This skill captures recurring GitHub Copilot review findings on route/client PSI
collectors. Apply it when changing code under any of:

- `plugin-route-model/` — leaf domain types (`ArmeriaRoute`, `RouteMatch`, `RouteProtocol`, …)
- `plugin-route-collectors/` — annotated/service-registration collectors, decorator/timeout
  support, `ArmeriaKotlinRouteCollector`, `RouteContributor` SPI, shared fixtures
- `plugin-route-spring/` — Spring MVC / Boot / config collectors, `ArmeriaDelegatedRouteCollector`
- `plugin-route-protocol/` — GraphQL / gRPC / Thrift / IDL / proto-text collectors
- `plugin-route-analysis/` — UI helpers, DocService, navigation, duplicate index, and
  `ArmeriaRouteAnalysisCollector` (production façade that always passes Spring/protocol contributors)

`ArmeriaRouteCollector` (core façade) and the `RouteContributor` / `RouteCollectContext` SPI
live in `plugin-route-collectors/`. Production callers use `ArmeriaRouteAnalysisCollector`.

- `ArmeriaRouteCollector`, `ArmeriaKotlinRouteCollector`, extended-registration collectors
- `ArmeriaRouteSupport`, decorator/annotated metadata helpers
- Spring Boot config collectors (`ArmeriaSpringConfigRouteCollector` and related)
- Duplicate route/registration inspections and indexes
- Java or Kotlin fixture tests for route discovery

## Core principle: resolve values, do not stringify PSI

Copilot frequently flags `expression.text` / `value.text` fallbacks that produce misleading
routes such as `/MY_PATH_CONST` or `/PATH_PREFIX` instead of evaluated values.

### Preferred order

1. **Literals** — `PsiLiteralExpression`, Kotlin string literals (`ArmeriaKotlinExpressionSupport`)
2. **Constants** — `evaluateConstant()` / field references / `KtNamedDeclaration` initializers
3. **References** — resolve `PsiReference` to qualified names or string values
4. **Last resort** — keep raw expression text for display only; do **not** pass through
   `normalizePath()` or duplicate-index keys unless the value is known to be a path literal

Reuse existing helpers before adding new extractors:

- `ArmeriaRouteSupport.extractStrings()` / `evaluateConstant()`
- `ArmeriaKotlinExpressionSupport` for Kotlin call chains
- `ArmeriaDecoratorSupport` / `ArmeriaKotlinDecoratorSupport` for decorator class names

When a path cannot be resolved, prefer omitting the route or marking metadata as unresolved
over inventing a normalized path that triggers false-positive duplicate detection.

## virtualHost scoping (Armeria semantics)

`virtualHost("host")` applies to registrations **after** the call in the fluent chain,
including inside the lambda body. It must **not** retroactively annotate earlier registrations.

Implementation checklist:

1. Walk the **forward** chained call sequence from each `virtualHost` site.
2. Track lambda-scoped registrations inside the `virtualHost` block.
3. Use `ArmeriaRouteVirtualHostAnnotator` / registration keys — do not rescan entire files.
4. Duplicate detection must include `virtualHostName` in the key so `/api` on different
   hosts are not grouped as duplicates.

Add regression tests for: backward chain (must not annotate), forward chain, nested virtualHost.

## Java / Kotlin parity

When changing registration or decorator parsing in Java collectors, check whether the Kotlin
path needs the same change:

| Shared helper | Purpose |
|---------------|---------|
| `ArmeriaBuilderCallHeuristics` | Detect builder-style registration calls |
| `ArmeriaRegistrationChainReducer` | Fluent `route()` / `routeDecorator()` chains |
| `CoreServiceRegistrationMethod` | Core service method names |
| `ArmeriaRouteVirtualHostAnnotator` | Apply hostname to registration keys |

Add or update tests in **both**:

- `ArmeriaExtendedRegistrationCollectorTest` (Java fixtures)
- `ArmeriaKotlinExtendedRegistrationCollectorTest` (Kotlin fixtures)

## Decorator and fluent chains

Common Copilot findings:

- Default `routeDecorator()` without `.path(...)` is `PathType.GLOB` with `/**`
- `methods(HttpMethod.POST, HttpMethod.PUT)` renders as `"POST, PUT"` (strip `HttpMethod.` prefix)
- `@Decorator({A.class, B.class})` array values need explicit handling — do not collapse to
  a single brace string via `renderMemberValue()` fallback
- Class literals in annotations: resolve to qualified names, not `{...}` text

## Annotated services

- Distinguish `@Get` / `@Post` / etc. from `service()` registrations — `service()` maps all
  HTTP methods; label and `RouteMatch` must reflect that.
- Path prefix annotations (`@PathPrefix`, `@Prefix`) compose with method-level paths; reuse
  `ArmeriaAnnotatedMetadataSupport` rather than duplicating annotation walks.
- For duplicate inspections, scope keys consistently (class, method, path, HTTP method,
  virtual host).

## Performance and caching

Route collection runs on every explorer refresh. Copilot flags full-project Java file walks.

Preferred approach (already used in `ArmeriaRouteCollector`):

1. **Index-backed searches** — `AnnotatedElementsSearch`, `FileTypeIndex`, `ReferencesSearch`
2. **Early exit** — skip files without Armeria imports/references before deep PSI walks
3. **Project cache** — `CachedValuesManager` keyed on `PsiModificationTracker`
4. **Production scope** — `collectionScope()` uses production sources; if excluding test sources,
   document the behavior change in the PR summary

Do not rescan fallback-processed files twice in one collection pass.

### FilenameIndex: name-driven lookup, not extension scans

When collecting a small, known filename set (e.g. `application*.{yml,yaml,properties}`):

```kotlin
// Bad — walks every YAML/properties file in large Spring repos on each recompute
FilenameIndex.getAllFilesByExt(project, "yml", scope)
    .filter { isApplicationConfigFile(it.name) }

// Good — filter filenames first, then resolve only matching VirtualFiles
FilenameIndex.getAllFilenames(project)
    .asSequence()
    .filter { isApplicationConfigFile(it) }
    .flatMap { name -> FilenameIndex.getVirtualFilesByName(name, scope) }
    .sortedWith(compareBy({ it.path }, { it.name }))
```

Sort candidates before any “first wins” dedupe — `FilenameIndex` iteration order is not stable,
so unsorted first-wins produces non-deterministic Route Explorer output across refreshes.

## Spring Boot config YAML / `.properties`

YAML (`application.yml` / `.yaml`) is read via optional IntelliJ YAML PSI
(`org.jetbrains.plugins.yaml`, `ArmeriaYamlSpringConfigReader`). When the YAML plugin is
not loaded, YAML config files are skipped; `.properties` still work.

Prefer key-level `PsiElement` navigation targets from YAML PSI (`YAMLKeyValue`) when emitting
config routes — do not attach the whole `PsiFile` if a more specific node is available.

`.properties` remain on a lightweight text/regex parser in `ArmeriaSpringConfigRouteCollector`.
Copilot repeatedly flags the same gaps (PR #211) for that path:

### YAML PSI

| Rule | Why |
|------|-----|
| Gate with `PluginManagerCore.isLoaded("org.jetbrains.plugins.yaml")` before calling the reader | Classloader only exposes YAML APIs when our optional `depends` is active |
| Keep YAML PSI imports in `ArmeriaYamlSpringConfigReader` (not the always-loaded collector entry) | Same optional-plugin isolation pattern as Kotlin helpers |
| Read only **top-level** `armeria` mappings (walk all YAML documents) | Nested `wrapper.armeria` / `armeria.foo.ports` must not emit routes |
| Prefer `YAMLScalar.textValue` / sequence items over raw node text | Quotes and comments are already stripped by the PSI |
| If the file is not a `YAMLFile` (e.g. Plain Text), parse via `YAMLElementGenerator.createDummyYamlWithText` and leave navigation on the original `PsiFile` | Restores discovery without pointing at a disposable PSI tree |
| Shared defaults / include expansion live in `SpringArmeriaConfigSemantics` | Keeps the optional YAML reader from depending on collector orchestration |

### Spring / Java `.properties`

| Rule | Why |
|------|-----|
| Last occurrence of a key wins | Spring/Java Properties semantics; `Regex.find` returns the first match |
| Indexed keys (`ports[0].protocols[0]`, `include[0]`) overwrite that index, not union | Appending then `distinct()` keeps stale values |
| Unindexed repeats (`include=docs` then `include=health`) are last-wins, not a set union | |
| Accept both `=` and `:` delimiters | Spring allows either |
| Anchor patterns to line start (`(?m)^…`) and skip `#` / `!` comment lines | Unanchored regex matches `# armeria.ports[0].port=8080` |

## Synthetic / config-sourced route emission

When emitting routes that are not PSI call-site registrations:

1. **`RouteMatch` must match HTTP capability** — “Generate HTTP Request” supports many HTTP
   `RouteMatch` values (`ANNOTATED_HTTP`, `SERVICE`, `CONFIG`, `RUNTIME`, …). For
   `RouteMatch.NON_HTTP` it is enabled **only for gRPC**; other `NON_HTTP` routes (Thrift,
   DocService, port bindings) are unsupported and method labels are dropped. Use `NON_HTTP`
   only for true non-HTTP protocols. HTTP config routes (health/metrics/actuator) use
   `RouteMatch.CONFIG` (or another HTTP-capable match still excluded from duplicate index).
2. **Distinct display paths** — Route Explorer renders pill + `path`. Port bindings must not use
   `path = "/"` (collides with real `/` routes); use a synthetic form such as `":<port>"`.
3. **Multi-value fields in labels** — if config has multiple protocols, store/join all of them
   in `protocol` / summary text, not only `protocols.first()`.
4. **Dedupe keys include distinguishing fields** — e.g. internal-service path alone is insufficient
   when `internal-services.port` differs across `application.yml` vs `.properties`; include port
   (and profile) in the key, or encode explicit precedence.
5. **Class names match scope** — if a collector parses YAML **and** properties, do not keep a
   `*Yaml*` type name; prefer a format-neutral name (`ArmeriaSpringConfigRouteCollector`).

## Proto / gRPC / Spring routes

- gRPC/proto routes: use Proto Editor PSI when available; cache merged proto routes
  (`mergeProtoRoutesIfEnabled`) to avoid repeated merges.
- Spring Boot config collectors: `ArmeriaSpringConfigRouteCollector` (YAML via optional YAML PSI,
  properties via text/regex) and `ArmeriaSpringBootRouteCollector` (Java `@Bean` PSI walks) —
  follow the config YAML/properties and synthetic-route rules above; guard optional Spring/Kotlin
  PSI behind availability checks.
- GraphQL / Thrift deduplication: align dedupe keys with HTTP route keys (path + method + host).

## Regression tests

When fixing collector behavior, add a focused fixture test that would have failed before the fix, in the module that owns the code under test:

```
plugin-route-collectors/src/test/...     — Java/Kotlin collector + extended registration PSI fixture tests
plugin-route-collectors/src/fastTest/... — collector-side pure unit tests (no fixtures)
plugin-route-spring/src/test/...         — Spring MVC/Boot/config + delegated collector PSI tests
plugin-route-spring/src/fastTest/...     — Spring config parse / semantics unit tests
plugin-route-protocol/src/test/...       — gRPC/IDL/Thrift/GraphQL PSI tests
plugin-route-protocol/src/fastTest/...   — proto-text unit tests
plugin-route-analysis/src/test/...       — DocService, duplicate index, UI detail-formatter PSI tests
plugin-route-analysis/src/fastTest/...   — UI helper (`Http*`, `RouteTreeBuilder`, observability) unit tests
```

Name tests after the behavior (`virtualHost_doesNotAnnotateEarlierRegistrations`), not the PR number.

## Related skills

- `intellij-armeria-plugin` — UI/i18n, index readiness, module placement, test task paths
- `gradle-tapi-mcp` — run `:plugin-route-<module>:test` / `fastTest` with correct `taskPath`
- `copilot-review-preflight` — pre-PR checklist
