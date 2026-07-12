---
name: armeria-route-psi-analysis
description: >-
  PSI and route-analysis implementation patterns for plugin-route-analysis. Use when
  editing ArmeriaRouteCollector, decorator/registration collectors, duplicate detection,
  virtualHost scoping, annotated-service parsing, or related regression tests.
---

# Armeria route PSI analysis

This skill captures recurring GitHub Copilot review findings on route/client PSI
collectors. Apply it when changing code under `plugin-route-analysis/`.

## When to use

- `ArmeriaRouteCollector`, `ArmeriaKotlinRouteCollector`, extended-registration collectors
- `ArmeriaRouteSupport`, decorator/annotated metadata helpers
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

## Proto / gRPC / Spring routes

- gRPC/proto routes: use Proto Editor PSI when available; cache merged proto routes
  (`mergeProtoRoutesIfEnabled`) to avoid repeated merges.
- Spring Boot YAML/Java config collectors: guard optional Kotlin/Spring PSI behind availability
  checks; keep Spring-specific code in `ArmeriaSpringBootRouteCollector`.
- GraphQL / Thrift deduplication: align dedupe keys with HTTP route keys (path + method + host).

## Regression tests

When fixing collector behavior, add a focused fixture test that would have failed before the fix:

```
plugin-route-analysis/src/test/...     — PSI fixture tests (platform harness)
plugin-route-analysis/src/fastTest/... — pure unit tests (no fixtures)
```

Name tests after the behavior (`virtualHost_doesNotAnnotateEarlierRegistrations`), not the PR number.

## Related skills

- `intellij-armeria-plugin` — UI/i18n, index readiness, module placement, test task paths
- `gradle-tapi-mcp` — run `:plugin-route-analysis:test` / `fastTest` with correct `taskPath`
- `copilot-review-preflight` — pre-PR checklist
