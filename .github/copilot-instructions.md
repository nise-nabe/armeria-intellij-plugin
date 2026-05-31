# Copilot instructions for armeria-intellij-plugin

- This repository is an IntelliJ IDEA plugin for Armeria. Implementation lives in the `plugin` Gradle module; shared build logic is in `build-logic`.
- Main plugin code lives in `plugin/src/main/kotlin/com/linecorp/intellij/plugins/armeria/`, resources in `plugin/src/main/resources/`, and the plugin is registered in `plugin/src/main/resources/META-INF/plugin.xml`.
- IDE and plugin dependency versions are pinned in `gradle/libs.versions.toml`; runtime platform version is also in root `gradle.properties` as `platformVersion`.
- Prefer small, focused changes in the existing feature areas: module/project generation, route explorer, inspections, and run configuration support.
- Route user-visible strings through `message(...)` and add the corresponding keys to `plugin/src/main/resources/messages/ArmeriaBundle.properties` instead of hard-coding UI text.
- Reuse existing IntelliJ PSI and platform APIs already used in the plugin instead of introducing parallel abstractions.
- Validate changes with `./gradlew --no-daemon :plugin:compileKotlin :plugin:test`.
- The build targets IntelliJ IDEA Ultimate `2026.1.1` and uses the Java 21 JetBrains toolchain, so keep new code compatible with that environment.
