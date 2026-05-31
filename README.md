# armeria-intellij-plugin

IntelliJ IDEA Plugin for [Armeria](https://armeria.dev/)

## Current focus

This plugin is evolving toward an Armeria cockpit inside IntelliJ IDEA:

- Route Explorer tool window for discovering annotated services and server registrations
- Armeria-specific duplicate route inspection for annotated services
- DocService URL detection in console output for faster local verification

## Project layout

| Path | Role |
| --- | --- |
| `plugin/` | **Canonical** plugin sources, file templates, starters, resources, tests, and `CHANGELOG.md` |
| `build-logic/` | Generic IntelliJ Platform convention (`com.linecorp.intellij.platform-plugin`) |
| `gradle/libs.versions.toml` | Kotlin, IntelliJ Platform Gradle Plugin, and IDE version pins |

Build and run locally:

```bash
./gradlew :plugin:runIde
./gradlew build
```

## Contributing

This repository uses [CODEOWNERS](/.github/CODEOWNERS) to automatically request reviews from the appropriate maintainers for pull requests.
