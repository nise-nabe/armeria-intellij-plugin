# armeria-intellij-plugin

IntelliJ IDEA Plugin for [Armeria](https://armeria.dev/)

## Current focus

This plugin is evolving toward an Armeria cockpit inside IntelliJ IDEA:

- Route Explorer tool window for discovering annotated services and server registrations
- Armeria Clients explorer for WebClient, gRPC, and Thrift client discovery
- Armeria run configuration with module classpath and main class selection
- Armeria-specific duplicate route inspection for annotated services
- DocService URL detection in console output for faster local verification

## Project layout

| Path | Role |
| --- | --- |
| `plugin-shared/` | Shared bundle, icons, and starters |
| `plugin-route-analysis/` | Route Explorer, inspections, and related analysis |
| `plugin-wizard/` | New Project Wizard templates and verification |
| `plugin/` | Aggregating plugin module, run config, Clients explorer, resources, and `CHANGELOG.md` |
| `build-logic/` | Generic IntelliJ Platform convention (`com.linecorp.intellij.platform-plugin`) |
| `gradle/libs.versions.toml` | Kotlin, IntelliJ Platform Gradle Plugin, and IDE version pins |

Build and run locally:

```bash
./gradlew :plugin:runIde
./gradlew build
```

## Contributing

This repository uses [CODEOWNERS](/.github/CODEOWNERS) to automatically request reviews from the appropriate maintainers for pull requests.
