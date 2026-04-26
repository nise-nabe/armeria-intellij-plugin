# Copilot instructions for armeria-intellij-plugin

- This repository is a single-module IntelliJ IDEA plugin for Armeria.
- Main plugin code lives in `src/main/kotlin`, plugin descriptors and resources live in `src/main/resources`, and the plugin is registered in `src/main/resources/META-INF/plugin.xml`.
- Keep Kotlin sources under the `com.linecorp.intellij.plugins.armeria` package prefix.
- Prefer small, focused changes in the existing feature areas: module/project generation, route explorer, inspections, and run configuration support.
- Route user-visible strings through `message(...)` and add the corresponding keys to `src/main/resources/messages/ArmeriaBundle.properties` instead of hard-coding UI text.
- Reuse existing IntelliJ PSI and platform APIs already used in the plugin instead of introducing parallel abstractions.
- Validate changes with `./gradlew --no-daemon compileKotlin test`.
- The build targets IntelliJ IDEA Ultimate `2026.1.1` and uses the Java 21 JetBrains toolchain, so keep new code compatible with that environment.
