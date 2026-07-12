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
| `plugin-route-analysis/` | Route/Client collectors, PSI analysis, route-related unit/fixture tests |
| `plugin-shared/` | Shared bundle helpers, cross-cutting PSI utilities, test fixtures |
| `plugin-wizard/` | New-project/module wizard, file templates, starter resources |
| `plugin/` | `plugin.xml` wiring, UI panels/actions, run configs, inspection registration |

Do not put collector logic in `plugin/` when it belongs in `plugin-route-analysis/`.
Do not add route-analysis tests under `plugin/src/test` when the class under test lives
in `plugin-route-analysis/`.

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
- **Properties file hygiene** — no duplicate keys; keep `[Unreleased]` changelog templates intact.
- **Stable identity** — when restoring tree selection or comparing routes, use stable fields
  (`routeMatch`, `httpMethod`, registration key), not localized labels like `methodLabel`.
- **HTML in renderers** — escape dynamic values (`descriptionText`, decorator names, unresolved
  expressions) before embedding in HTML tooltips; unresolved PSI text may contain `<` or `&`.

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

## Test task paths (multi-module)

Match the Gradle task to the module that contains the test class:

| Test location | Gradle task |
|---------------|-------------|
| `plugin-route-analysis/src/test` | `:plugin-route-analysis:test` |
| `plugin-route-analysis/src/fastTest` | `:plugin-route-analysis:fastTest` |
| `plugin/src/test` | `:plugin:test` |
| `plugin-wizard/src/test` | `:plugin-wizard:test` |

When documenting a test plan in a PR, use the module-qualified task. With Gradle MCP,
set `taskPath` accordingly (e.g. `":plugin-route-analysis:test"`).

## Related skills

- `armeria-route-psi-analysis` — PSI collectors, route semantics, constant evaluation
- `gradle-tapi-mcp` — build/test execution and `taskPath` usage
- `copilot-review-preflight` — pre-PR checklist aggregating common review themes
