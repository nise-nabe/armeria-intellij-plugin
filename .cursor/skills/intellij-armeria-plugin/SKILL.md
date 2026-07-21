---
name: intellij-armeria-plugin
description: >-
  IntelliJ Platform plugin conventions for this repository. Use when implementing
  or editing plugin UI, run configurations, tool windows, inspections, actions,
  or any Kotlin code under plugin/, plugin-shared/, or plugin-wizard/.
---

# IntelliJ Armeria plugin conventions

This skill captures recurring GitHub Copilot review findings on plugin implementation
in this repository. Apply it **during implementation**, not only before opening a PR.

## When to use

- Adding or changing tool windows, panels, actions, run configurations, inspections
- Touching user-visible text, Swing renderers, or FormBuilder fields
- Calling PSI/index APIs from UI or run-configuration startup paths
- Deciding which Gradle module should own new code or tests

## Module placement

| Module | Owns |
|--------|------|
| `plugin-route-model/` | Leaf domain types (`ArmeriaRoute`, `RouteMatch`, `RouteProtocol`, `PathType`, `DelegationKind`, `CoreServiceRegistrationMethod`, `ArmeriaRouteMetadata`) |
| `plugin-route-collectors/` | Annotated/service-registration collectors, decorator/timeout/annotation helpers, `ArmeriaKotlinRouteCollector`, `psi` traversal, `ArmeriaRouteSupport`, `RouteContributor`/`RouteCollectContext` SPI, shared test fixtures (`ArmeriaFixtureTestBase`, stubs) |
| `plugin-route-spring/` | `ArmeriaSpringBootRouteCollector`, `ArmeriaKotlinSpringBootRouteCollector`, `ArmeriaSpringMvcRouteCollector`, `ArmeriaSpringConfigRouteCollector`, `ArmeriaYamlSpringConfigReader`, `SpringArmeriaConfig*`, `ArmeriaDelegatedRouteCollector` |
| `plugin-route-protocol/` | `ArmeriaGraphqlRouteCollector`, `ArmeriaGrpcRouteCollector`, `ArmeriaThriftRouteCollector`, `ArmeriaIdlRouteSupport`, `ArmeriaProtoTextSupport` |
| `plugin-route-analysis/` | Route Explorer UI helpers (`ui/`), DocService support (`docservice/`), navigation (`navigation/`), duplicate index (`duplicate/`), and `ArmeriaRouteAnalysisCollector` (production façade that always supplies Spring/protocol contributors) |
| `plugin-shared/` | Shared bundle helpers, cross-cutting PSI utilities, test fixtures |
| `plugin-wizard/` | New-project/module wizard, file templates, starter resources |
| `plugin/` | `plugin.xml` wiring, UI panels/actions, run configs, inspection registration |

Do not put collector logic in `plugin/` when it belongs in one of the `plugin-route-*` modules.
Do not add route-analysis tests under `plugin/src/test` when the class under test lives in a
`plugin-route-*` module.

Cross-module rules to keep the dependency graph acyclic:

- `plugin-route-model` may only depend on `plugin-shared`.
- `plugin-route-collectors` may depend on `plugin-route-model` and `plugin-shared`.
- `plugin-route-spring` and `plugin-route-protocol` may depend on `plugin-route-collectors`
  (transitively `plugin-route-model` + `plugin-shared`). Neither may depend on the other or on
  `plugin-route-analysis`.
- `plugin-route-analysis` `api`s the four modules above so `plugin/` can consume them transitively.
- `plugin/` only depends on `plugin-route-analysis` for production wiring; test-side it consumes
  `testFixtures(project(":plugin-route-collectors"))`.

## Internationalization (i18n)

All user-visible strings must go through `message(...)` with keys in
`plugin-shared/src/main/resources/messages/ArmeriaBundle.properties`.

```kotlin
// Good
add(object : DumbAwareAction(message("route.explorer.action.refresh")) { ... })

// Bad — hard-coded English in UI
statusLabel.text = "Refreshing Armeria routes..."
```

Additional rules Copilot repeatedly flags:

- **FormBuilder labels** — match existing plugin style; field labels use a trailing colon.
- **Properties file hygiene** — no duplicate keys; no unused keys added “for later”; keep
  `[Unreleased]` changelog templates intact. Completion docs / tooltip descriptions are
  user-visible — route them through the bundle too (not hard-coded English maps).
- **Status copy matches capability** — if the collector also scans `application.yaml` and
  profile variants (`application-dev.properties`), the empty/status string must say so.
- **Stable identity** — when restoring tree selection or comparing routes, use stable fields
  (`routeMatch`, `httpMethod`, registration key), not localized labels like `methodLabel`.
- **HTML in renderers** — escape dynamic values (`descriptionText`, decorator names, unresolved
  expressions) before embedding in HTML tooltips; unresolved PSI text may contain `<` or `&`.

## Optional dependency config files (`*-integration.xml`)

Optional config files loaded via `config-file` / `depends` (YAML, Kotlin, Spring, …) must
contain **only** extensions that require that plugin. Copilot flags core UI registered only
behind an optional dependency (PR #212):

| Put in main `plugin.xml` | Put in optional `*-integration.xml` |
|--------------------------|-------------------------------------|
| Tool windows, actions, explorers that work without the optional plugin | Completion contributors, PSI annotators, language-specific handlers |

Example: Spring Boot Config explorer works for `.properties` without the YAML plugin — register
the tool window in `plugin.xml`, leave only `CompletionContributor` for YAML in
`yaml-integration.xml`. Disabling YAML must not hide the explorer.

## Swing table / tree renderers

Cell renderers are reused. Always reset shared state for every cell, and map view → model
indices when a sorter may be enabled (PR #212):

```kotlin
override fun getTableCellRendererComponent(...): Component {
    val c = super.getTableCellRendererComponent(...)
    val modelRow = table.convertRowIndexToModel(row)
    toolTipText = null          // reset before optional set
    font = table.font           // reset bold/italic before optional set
    // then set tooltip / bold only for the cells that need them, using modelRow
    return c
}
```

On refresh **failure**, clear the table/tree model as well as updating the status label —
leaving stale rows under an error message is misleading.

## VirtualFile text loading

Prefer charset-aware VFS APIs over hard-coded UTF-8 byte decoding:

```kotlin
// Bad — ignores project/file encoding
String(vf.contentsToByteArray(), Charsets.UTF_8)

// Good
LoadTextUtil.loadText(vf).toString()
// or: vf.charset / EncodingManager when building a Reader
```

## Optional Kotlin plugin safety

Kotlin support is optional. Classes loaded on every IDE startup must not hard-reference
Kotlin plugin types (`KotlinFileType`, `KtFile`, `KtNamedFunction`, etc.) at the class level.

```kotlin
// Bad — can cause NoClassDefFoundError when Kotlin plugin is absent/disabled
import org.jetbrains.kotlin.idea.KotlinFileType
FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)

// Good — guard and delegate to a Kotlin-only helper loaded only when available
if (isKotlinPluginAvailable()) {
    ArmeriaKotlinRouteCollector.collect(...)
}
```

Checklist:

1. Gate Kotlin paths with `isKotlinPluginAvailable()` (plugin installed **and** enabled).
2. Keep Kotlin-specific imports in `ArmeriaKotlin*` files, not in shared entry points like
   `ArmeriaRouteCollector` unless the class is only loaded when Kotlin is present.
3. Prefer extracting Kotlin iteration into `ArmeriaKotlinRouteCollector` / `ArmeriaKotlin*Support`.

## Index readiness and dumb mode

Index-backed APIs (`FileTypeIndex`, `AnnotatedElementsSearch`, `ReferencesSearch`,
`resolveMethod`, full-project `collect`) throw `IndexNotReadyException` while indexing.

| Context | Required handling |
|---------|-------------------|
| Run configuration startup (`execute`, `createState`) | Best-effort: catch `IndexNotReadyException`, return `null`/skip feature |
| Tool window registered `DumbAware` | Defer index-heavy refresh until smart mode, or catch and show empty state |
| Background refresh | Do not assume indices are ready on first open |

Existing pattern: `ArmeriaRunProfileState.resolveDocServiceUrl()` catches
`IndexNotReadyException` and skips the console hint.

## Background work and UI lifecycle

Route/client explorer refresh uses `ReadAction.nonBlocking`, not blocking `ReadAction.run`
on the EDT.

Required patterns:

```kotlin
ReadAction.nonBlocking<List<ArmeriaRoute>> { ArmeriaRouteCollector.collect(project) }
    .coalesceBy(this)          // collapse duplicate refreshes
    .expireWith(this)          // cancel when panel/tool window is disposed
    .finishOnUiThread(ModalityState.defaultModalityState()) { routes -> updateTree(routes) }
    .submit(AppExecutorUtil.getAppExecutorService())
```

Never call heavy `ArmeriaRouteCollector.collect()` synchronously inside `execute()` or
action `actionPerformed` without a documented reason.

## Run configuration entry points

When detecting JVM `main` classes:

- Use `PsiMethodUtil.hasMainInClass` / `hasMainMethod` / `findMainMethod`.
- Do **not** treat any `static` method named `main` as valid — invalid signatures
  (`static void main()`, `static int main(String[])`) are not runnable.
- For Kotlin top-level `main`, resolve via facade class (`MainKt`) in `ArmeriaKotlinMainClassSupport`.
- Gate Armeria run configs on actual server usage (`Server.builder()` / Armeria application
  references), not any `com.linecorp.armeria` import in the file.

## IntelliJ API hygiene

- Prefer `project.basePath` over deprecated `Project.baseDir`; avoid `File(".")` fallbacks.
- Smart pointers: `SmartPointerManager.getInstance(project).createSmartPsiElementPointer(element)`.
- Enum naming in this codebase: prefer ALL_CAPS entries (e.g. `RouteProtocol.HTTP`), not PascalCase.
- `const val` names use `UPPER_SNAKE_CASE` when they are true constants.
- Hot-path predicates (filename checks on every completion/collector call) must not allocate
  `setOf(...)` / `listOf(...)` per invocation — hoist to a `private val` / companion constant.

## YAML / properties completion contributors

When completing nested config keys under a parent mapping:

1. **Prefix-match the leaf lookup string**, not the full dotted suggestion path — typing `po`
   under `internal-services:` must match `port`, even if the suggestion id is
   `armeria.internal-services.port`.
2. **Walk `YAMLKeyValue` parents** when computing the current key path — stopping at
   `YAMLMapping` / `YAMLSequenceItem` alone truncates nested block mappings
   (`server: { port: ... }` → path becomes just `port`).
3. **Omit empty documentation tails** — do not append `" — "` (or similar) when there is no
   description; dangling separators show up in the lookup list.

## Test task paths (multi-module)

Match the Gradle task to the module that contains the test class:

| Test location | Gradle task |
|---------------|-------------|
| `plugin-route-collectors/src/test` | `:plugin-route-collectors:test` |
| `plugin-route-collectors/src/fastTest` | `:plugin-route-collectors:fastTest` |
| `plugin-route-spring/src/test` | `:plugin-route-spring:test` |
| `plugin-route-spring/src/fastTest` | `:plugin-route-spring:fastTest` |
| `plugin-route-protocol/src/test` | `:plugin-route-protocol:test` |
| `plugin-route-protocol/src/fastTest` | `:plugin-route-protocol:fastTest` |
| `plugin-route-analysis/src/test` | `:plugin-route-analysis:test` |
| `plugin-route-analysis/src/fastTest` | `:plugin-route-analysis:fastTest` |
| `plugin/src/test` | `:plugin:test` |
| `plugin-wizard/src/test` | `:plugin-wizard:test` |

When documenting a test plan in a PR, use the module-qualified task. With Gradle MCP,
set `taskPath` accordingly (e.g. `":plugin-route-collectors:test"`).

## Related skills

- `armeria-route-psi-analysis` — PSI collectors, route semantics, constant evaluation
- `gradle-tapi-mcp` — build/test execution and `taskPath` usage
- `copilot-review-preflight` — pre-PR checklist aggregating common review themes
