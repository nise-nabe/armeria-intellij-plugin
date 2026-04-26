---
name: intellij-idea-plugin-development
description: Guidance for implementing, debugging, and reviewing IntelliJ IDEA plugin changes in this repository. Use this when working on plugin features, actions, inspections, tool windows, run configurations, or project/module wizard integrations.
---

# IntelliJ IDEA plugin development for armeria-intellij-plugin

Use this skill when the task is about changing or investigating this repository's IntelliJ IDEA plugin behavior.

## Repository context

- This repository is an IntelliJ IDEA plugin for Armeria.
- Production code lives under `src/main/kotlin`.
- Resources such as plugin metadata, file templates, and messages live under `src/main/resources`.
- Tests live under `src/test/kotlin`.

## Build and validation

- Validate changes with `./gradlew --no-daemon compileKotlin test`.
- The project targets IntelliJ IDEA Ultimate `2026.1`.
- The Gradle Java toolchain for the plugin code is Java `21` with vendor `JETBRAINS`.

## Plugin-specific conventions

- Keep Kotlin code under the `com.linecorp.intellij.plugins.armeria` package prefix.
- Prefer existing IntelliJ Platform APIs and project patterns already used in the repository.
- User-facing strings should come from `messages.ArmeriaBundle` via the `message(...)` helper instead of hard-coded literals.

## Useful areas to inspect

- `src/main/kotlin/module`: project/module wizard integration
- `src/main/kotlin/run`: run configuration support
- `src/main/kotlin/explorer`: route explorer features
- `src/main/resources/messages`: localized user-facing strings
- `src/main/resources/META-INF`: plugin registration and extension points

## Working style

1. Inspect existing implementations in the relevant package before editing.
2. Make small, focused changes that fit the existing plugin architecture.
3. Update resource bundles or plugin metadata when a change affects UI text or registration.
4. Run `./gradlew --no-daemon compileKotlin test` before finishing.
