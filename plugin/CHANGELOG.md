# Changelog

## [Unreleased]

### Added

- Generate HTTP Request fills `.http` files from route metadata: `Content-Type` / `Accept` from `@Consumes` / `@Produces` (including `@ConsumesJson` / `@ProducesJson`), `@MatchesHeader` request headers, a JSON body for JSON consumes, and `{id}` path placeholders. GraphQL operations get a POST `/graphql` stub; gRPC method files include a JSON body placeholder.
- Plugin install, update, enable, and disable no longer require an IDE restart.

### Changed

### Deprecated

### Removed

### Fixed

- Endpoints tool window Framework filter lists Armeria server routes and Armeria client factories as distinct entries (`Armeria` vs `Armeria Client`) instead of two identical Armeria items.

### Security

## [0.3.0] - 2026-08-15

### Added

- Armeria annotated HTTP routes and `Server.builder()` registrations appear in the Endpoints tool window (Framework = Armeria, Type = HTTP Server), including HTTP Client URL targets. Prefix and `serviceUnder` paths are prefix URL targets; glob `*` / `**` segments are wildcards. Armeria client factories (`WebClient`, `RestClient`, `BlockingWebClient`, gRPC, Thrift, Retrofit) with HTTP-like request URIs appear as Type = HTTP Client; EndpointGroup-backed clients and discovery URIs such as `zk://` are omitted. Spring MVC mappings stay on Spring’s provider.
- Armeria Clients explorer discovers `RestClient` and `BlockingWebClient` factories and conversions (`WebClient.asRestClient()`, `WebClient.blocking()`, including Kotlin `!!` / `?.` chains), labels additional client decorators (`OAuth2Client`, Auth, Throttling, Decoding/Encoding), surfaces EndpointGroup kinds (DNS, ZooKeeper, Eureka, Consul, health-checked, including nested group URIs), and jumps to overlapping Armeria Services routes (toolbar and gutter; reverse jump from Route Explorer). Matching uses HTTP-like request URIs and does not treat discovery connection strings such as `zk://` as request paths.
- Spring Boot `ArmeriaSettings` completion for YAML and `application.properties` (including `docs-path` / `health-check-path` / `metrics-path` and `internal-services.include` values, YAML nested keys before a colon is typed, and `.properties` whitespace delimiters), plus jump from the Spring Boot Config explorer to `ArmeriaServerConfigurator` / `DocServiceConfigurator` / `HealthCheckServiceConfigurator` / `MetricCollectingServiceConfigurator` beans.
- Annotated-service editor support: path-variable / `@Param` mismatch inspection (Java and Kotlin), `@Param` completion and rename against route path variables, `@Header` / `@Cookie` value completion, Generate Route Method in Kotlin (including `suspend` when `armeria-kotlin` is on the classpath), POST JSON route stubs (`@Post` + `@ConsumesJson` + `@ProducesJson`), and Route Explorer labels that distinguish same-path methods that differ only by `@MatchesHeader`.
- Route Explorer classifies `Server.builder().service()` / `serviceUnder()` targets from resolved Armeria types (`com.linecorp.armeria`), walking constructors, builder/`addService` chains, typed variables, `.decorate()` receivers, and Kotlin `as` casts. User types outside that package stay HTTP even when the display name matches a built-in. `DocService`, `PrometheusExpositionService`, `GrpcService`, `FileService`, and servlet mounts show the correct protocol and Open DocService / duplicate-index behavior.
- `@RegisterExtension` factory methods on Java static/instance methods and Kotlin class, named-object, or `@JvmStatic` companion methods are discovered for gutter run markers, blocking-client inspection scoping, and Generate Test Method.
- PSI fixture regression tests for duplicate annotated route and duplicate route registration inspections (Java route highlighting, Java/Kotlin registration inspection scenarios).
- ktlint via `com.linecorp.intellij.ktlint` convention (`ktlint_official` style); `ktlintCheck` runs as part of `check` / `build`.
- gRPC Route Explorer discovery uses Proto Editor PSI when `idea.plugin.protoeditor` is available (RPC-level navigation), with fallback to the existing `.proto` text parser.
- IDE support for Armeria JUnit 5 integration tests: `@RegisterExtension` `ServerExtension` gutter markers, blocking-route inspection in tests, and Generate Test Method from Route Explorer.
- gRPC gutter icons on `rpc` keywords in `.proto` files (when Proto Editor is installed), showing the resolved gRPC path in the tooltip.
- Scala sources in Route Explorer and Clients Explorer (annotated services, `Server.builder()` registrations, and WebClient/gRPC/Thrift factories).
- Route Explorer expands Spring MVC controller mappings under Armeria `serviceUnder` prefix mounts as delegated children (exact `.service()` Tomcat mounts stay badge-only).

### Changed

- Combine the Armeria Services and Armeria Clients explorers into one Armeria tool window with Services and Clients tabs. Go to Matching Route/Client and DocService runtime sync switch to the matching tab.
- Plugin ID is `com.linecorp.armeria` (was `com.linecorp.intellij.armeria-intellij-plugin`) so the descriptor no longer includes the word `intellij`. Existing installs are treated as a different plugin.
- Kotlin PSI helpers for call names, named or positional arguments, and string extraction live on `ArmeriaKotlinExpressionSupport` and are reused by route collectors, client collectors, navigation, and line markers. Virtual-host annotation of a registration-key set lives on `ArmeriaRouteVirtualHostAnnotator`.
- Route Explorer caches proto route merging so Refresh no longer re-scans `.proto` files on every collect.
- Route Explorer reads Spring Boot `application.yml` / `.yaml` via IntelliJ YAML PSI (key-level navigation); `.properties` parsing is unchanged. YAML config is skipped when the YAML plugin is unavailable.
- Split `plugin-route-analysis` `explorer` sources into focused packages (`model`, `collector`, `spring`, `protocol`, `docservice`, `support`, `duplicate`, `navigation`, `ui`).
- Split the route-analysis codebase into five acyclic Gradle modules: `plugin-route-model` (leaf domain types), `plugin-route-collectors` (annotated / service-registration collectors, decorator/timeout support, `RouteContributor` SPI, public `ArmeriaRouteCollector`, shared test fixtures), `plugin-route-spring` (Spring MVC / Boot / config collectors), `plugin-route-protocol` (GraphQL / gRPC / Thrift), and `plugin-route-analysis` (UI helpers, DocService, navigation, duplicate index, `ArmeriaRouteAnalysisCollector`). `plugin-route-analysis` `api`-exports the other route modules for compile; `plugin` composes `plugin-shared`, `plugin-wizard`, and all `plugin-route-*` modules into the main plugin JAR. Test fixtures are consumed from `plugin-route-collectors`.
- Move DocService runtime route pointer/factory from `explorer.navigation` to `explorer.model.runtime` in `plugin-route-model` so `navigation` is jump-to-source only.

### Fixed

- Stop packaging Kotlin stdlib and JetBrains annotations into the plugin ZIP so a real IntelliJ IDEA install no longer hits a stdlib classloader conflict at plugin load. Sibling modules (`plugin-wizard`, `plugin-route-*`) are composed into the main plugin JAR, and `plugin.xml` now includes the required description.
- Blocking-client inspection resolves compile-time path constants (Java `static final`, Kotlin `const val`, and const-interpolated Kotlin string templates) instead of only string literals.
- Test helper resolution and blocking-client inspection no longer guess which `ServerExtension` applies when multiple `@RegisterExtension` fields are in scope.
- Spring MVC Route Explorer discovery finds mappings declared on generic base types/interfaces when the concrete controller substitutes type parameters (e.g. `Handler<T>` → `StringHandler`), including multi-level unannotated overrides and interface mappings satisfied by an inherited superclass method.
- Bundle `plugin-shared` (including `ArmeriaBundle`) into the main plugin JAR so installing only `plugin-*.jar` no longer fails inspection-profile saves with `ClassNotFoundException: ArmeriaBundleKt`.
- Route Explorer deduplicates GraphQL and Thrift IDL routes per module when the same operation appears in multiple schema or `.thrift` files.
- DocService runtime route sync activates the Armeria Services tool window when needed so routes apply even if it was closed before the fetch completed.
- Route Explorer Refresh no longer clears DocService-synced runtime routes; synced routes stay until the next sync replaces them.
- Generate Test Method inserts into Kotlin `object` declarations and does not guess a module when more than one is in scope.
- Blocking-client inspection skips `*Test` files that live in main sources rather than test sources.

## [0.2.0] - 2026-07-18

### Added

- Extended Armeria Clients explorer with Retrofit/WebClient transport detection, client decorator collection, EndpointGroup usage, and a structured detail panel.
- Live templates for annotated route methods, `Server.builder()`, and `WebClient` decorator chains (Java/Kotlin).
- Intention action to generate `@Get` route method stubs in annotated Java service classes.

### Fixed

- Armeria run configuration discovery now resolves Kotlin top-level `fun main()` via the file facade (`MainKt`), matching New Project Wizard Kotlin templates.

## [0.1.0] - 2026-07-04

### Added

- Implemented Armeria run configuration with module classpath and main class selection.
- Added project-wide duplicate route registration inspection for ServerBuilder and annotated routes, with module-scoped caching and cross-registration conflict detection.
- Added programmatic `ServerBuilder.decorator()` detection in Route Explorer.
- Added HTTP Request file generation from Route Explorer routes, with method-aware filenames and toolbar enablement tied to the current tree selection.
- Added Spring Boot `@Bean` Server registration discovery for Java and Kotlin sources.
- Added timeout and blocking annotation hints in Route Explorer details.
- Added Armeria Clients tool window for WebClient, GrpcClient, and ThriftClient discovery.
- Added GraphQL schema and Thrift IDL route discovery with classpath gating and operation-level targets.
- Added gRPC route discovery from `.proto` service definitions with brace-aware parsing and a registry kill-switch.
- Added New Project Wizard sample `Main` and `logback.xml` generation.
- Added JUnit 5 Armeria service test template generation in New Project Wizard.
- Added DocService runtime route sync action in Route Explorer.
- Added Kotlin source support to Route Explorer for annotated services and Server.builder registrations.
- Added Velocity-based regression tests for New Project Wizard file templates.
- Added `plugin-wizard/src/test/resources/wizard-verification-matrix.md` documenting representative wizard scenarios.
- Added an Armeria Route Explorer tool window for discovering annotated services and registered routes.
- Added a duplicate annotated route inspection for Armeria HTTP services.
- Added DocService URL detection in console output.

### Fixed

- Fixed Armeria Clients explorer listing no-arg `builder()` calls and non-Armeria `WebClient` false positives in Kotlin fallback resolution.
- Fixed Kotlin client URI extraction, `GrpcClients`/`ThriftClients.newClient` discovery, and async PSI collection in Armeria Clients explorer.
- Fixed duplicate Kotlin annotated routes and false-positive service registrations in Route Explorer.
- Fixed false-positive route detection for unqualified `service` calls inside `also`/`let` blocks on `Server.builder()`.
- Fixed FQCN `com.linecorp.armeria.server.Server.builder()` detection in Kotlin fallback scanning.
- Fixed potential NPE when building Kotlin service registration keys for non-physical PSI files.
- Fixed unresolved-target detection for Kotlin `build()`/`builder()` wrapper expressions.
- Fixed Kotlin `ServerBuilder?` and generic type spellings not recognized as server-builder receivers.
- Fixed Kotlin path extraction for Java `static final` constants and `ServerBuilder` extension functions.
- Fixed Kotlin `ServerBuilder` typealias variables not recognized as server-builder receivers.
- Aligned Kotlin service registration discovery with the Java collector via shared PSI delegation.
- New Project Wizard: emit `armeria-tomcat8` in Gradle and Maven templates when selected.
- New Project Wizard: align Scala optional dependencies and Scala build setup in Gradle (Groovy) and Maven templates with Gradle (Kotlin DSL).
