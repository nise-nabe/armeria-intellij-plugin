# Changelog

## [Unreleased]
### Added

- Added fixture regression tests for duplicate route and duplicate registration inspections (Java and Kotlin).

### Changed

### Deprecated

### Removed

### Fixed

- Armeria run configuration discovery now resolves Kotlin top-level `fun main()` via the file facade (`MainKt`), matching New Project Wizard Kotlin templates.
- DocService runtime sync activates the Armeria Services tool window when needed so synced routes can be applied even if Route Explorer was never opened.

### Security

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
