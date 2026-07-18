# Copilot instructions for armeria-intellij-plugin

- This repository is an IntelliJ IDEA plugin for Armeria. Implementation lives in the `plugin` Gradle module; generic IPGP/Kotlin toolchain logic is in `build-logic` (`com.linecorp.intellij.platform-plugin`). Armeria-specific IDs, dependencies, and `pluginConfiguration` belong in `plugin/build.gradle.kts`.
- Main plugin code lives in `plugin/src/main/kotlin/com/linecorp/intellij/plugins/armeria/`, resources in `plugin/src/main/resources/`, and the plugin is registered in `plugin/src/main/resources/META-INF/plugin.xml`.
- IDE and plugin dependency versions are pinned in `gradle/libs.versions.toml`, including the IntelliJ IDEA platform version (`idea-platform`).
- Prefer small, focused changes in the existing feature areas: module/project generation, route explorer, inspections, and run configuration support.
- Route user-visible strings through `message(...)` and add the corresponding keys to `plugin/src/main/resources/messages/ArmeriaBundle.properties` instead of hard-coding UI text.
- Reuse existing IntelliJ PSI and platform APIs already used in the plugin instead of introducing parallel abstractions.
- **Gradle tasks:** prefer the `gradle` MCP server (`nise-nabe/gradle-tapi-mcp-server` v0.5.1) configured in `.github/mcp.json`. Read `.github/skills/gradle-tapi-mcp/SKILL.md` for workflows. Use `gradle_run_tasks` / `gradle_run_tests` with `background: true` and poll `gradle_get_build_status` for long runs. Fall back to `./gradlew` only when MCP is unavailable.
- **Kotlin code intelligence:** `.github/lsp.json` configures JetBrains [kotlin-lsp](https://github.com/Kotlin/kotlin-lsp) for `.kt` / `.kts` files. The server is installed by `copilot-setup-steps` via `.github/scripts/install-kotlin-lsp.sh`.
- Validate changes with MCP: `gradle_run_tasks` `[":plugin:compileKotlin", ":plugin:compileTestKotlin"]`, then `gradle_run_tests` for changed test classes (one at a time, background + poll). Before finishing, run `gradle_run_tasks` `["build"]` with `background: true` (or shell `./gradlew build` if MCP times out).
- The build targets IntelliJ IDEA Ultimate `2026.2` and uses the Java 21 JetBrains toolchain, so keep new code compatible with that environment.
