# armeria-intellij-plugin

IntelliJ IDEA Plugin for [Armeria](https://armeria.dev/)

## Current focus

This plugin is evolving toward an Armeria cockpit inside IntelliJ IDEA:

- Armeria explorer tool window with Services (annotated services and server registrations) and Clients (WebClient, gRPC, and Thrift) tabs
- Armeria run configuration with module classpath and main class selection
- Armeria-specific duplicate route inspection for annotated services
- DocService URL detection in console output for faster local verification

## Project layout

| Path | Role |
| --- | --- |
| `plugin-shared/` | Shared bundle, icons, and starters |
| `plugin-route-analysis/` | Route Explorer, inspections, and related analysis |
| `plugin-wizard/` | New Project Wizard templates and verification |
| `plugin-test-support/` | Armeria test-support inspections, generated test methods, and JUnit `ServerExtension` support |
| `plugin-explorer/` | Route/Client Explorer UI, client endpoint analysis, OpenAPI/HTTP request generation, and DocService actions |
| `plugin-inspections/` | Java/Kotlin/Scala production inspections (excluding test-support inspections), quick fixes, and inspection support logic |
| `plugin/` | Aggregating plugin module, run config, resources, and `CHANGELOG.md` |
| `build-logic/` | Generic IntelliJ Platform convention (`com.linecorp.intellij.platform-plugin`) |
| `gradle/libs.versions.toml` | Kotlin, IntelliJ Platform Gradle Plugin, and IDE version pins |

Build and run locally:

```bash
./gradlew :plugin:runIde
./gradlew build
```

## Contributing

This repository uses [CODEOWNERS](/.github/CODEOWNERS) to automatically request reviews from the appropriate maintainers for pull requests.
