# Agent instructions

## Cursor Cloud specific instructions

IntelliJ Platform plugin for Armeria. Gradle multi-project build (`build-logic` + `plugin`). No web UI or Docker services.

### Prerequisites

- **JDK 25** (JetBrains toolchain via `gradle/gradle-daemon-jvm.properties` and Foojay resolver).
- Gradle wrapper (`./gradlew`) downloads the distribution and dependencies on first use.

### MCP: Gradle Tooling API

The `gradle` MCP server (`nise-nabe/gradle-tapi-mcp-server` v0.2.2) is configured in `.cursor/mcp.json`. The install script downloads the release JAR to `~/.local/share/gradle-tapi-mcp-server/` and sets `GRADLE_PROJECT_DIR` to the workspace root.

Prefer token-efficient MCP workflows documented in `.cursor/skills/gradle-tapi-mcp/SKILL.md`:

1. `gradle_get_build_environment` for resolved Gradle/Java versions
2. `gradle_get_project_overview` for module hierarchy
3. `gradle_run_tasks` with `["build"]` or targeted `:plugin:test` when verification is needed

### Build, test, lint

| Goal | Command |
|------|---------|
| Full verify | `./gradlew build` |
| Compile plugin | `./gradlew :plugin:compileKotlin` |
| Unit tests | `./gradlew :plugin:test` |
| Run IDE sandbox | `./gradlew :plugin:runIde` |
| Fast CI-style check | `./gradlew --no-daemon :plugin:compileKotlin :plugin:test` |

There is no separate lint task; `./gradlew build` is the compile/test gate.

### Project layout

| Path | Role |
|------|------|
| `plugin/` | Plugin sources, resources, tests, `CHANGELOG.md` |
| `build-logic/` | Shared IntelliJ Platform Gradle conventions |
| `gradle/libs.versions.toml` | Version pins (Kotlin, IPGP, IDEA platform) |

Main Kotlin code: `plugin/src/main/kotlin/com/linecorp/intellij/plugins/armeria/`. User-visible strings go through `message(...)` and `ArmeriaBundle.properties`.
