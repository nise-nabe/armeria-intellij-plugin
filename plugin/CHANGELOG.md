# Changelog

## [Unreleased]
### Added
- Implemented Armeria run configuration with module classpath and main class selection via ApplicationConfigurationBase.
- Added project-wide duplicate route registration inspection for ServerBuilder and annotated routes.
- Added programmatic `ServerBuilder.decorator()` detection in Route Explorer.
- Added gRPC route discovery from `.proto` service definitions with brace-aware parsing and a registry kill-switch.
- Added Kotlin source support to Route Explorer for annotated services and Server.builder registrations.
- Added Velocity-based regression tests for New Project Wizard file templates.
- Added `plugin/src/test/resources/wizard-verification-matrix.md` documenting representative wizard scenarios.
- Added an Armeria Route Explorer tool window for discovering annotated services and registered routes.
- Added a duplicate annotated route inspection for Armeria HTTP services.
- Added DocService URL detection in console output.

### Changed

### Deprecated

### Removed

### Fixed
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

### Security
