# Agent instructions

## Cursor Cloud specific instructions

IntelliJ Platform plugin for Armeria. Gradle multi-project build (`build-logic` + `plugin`). No web UI or Docker services.

### Prerequisites

- **Gradle daemon JVM**: Adoptium 25 (pinned in `gradle/gradle-daemon-jvm.properties`; Foojay resolver downloads it).
- **Compile toolchain**: Java 21 JetBrains (configured in `build-logic/src/main/kotlin/com.linecorp.intellij.platform-plugin.gradle.kts` and inherited by `plugin/`).
- Gradle wrapper (`./gradlew`) downloads the distribution and dependencies on first use.

### MCP: Gradle Tooling API

The `gradle` MCP server (`nise-nabe/gradle-tapi-mcp-server` v0.2.3) is configured in `.cursor/mcp.json`. The install script downloads the release JAR to `~/.local/share/gradle-tapi-mcp-server/`, verifies its SHA-256, and exposes it via a stable `gradle-tapi-mcp-server.jar` symlink. `GRADLE_PROJECT_DIR` is set to the workspace root.

Prefer token-efficient MCP workflows documented in `.cursor/skills/gradle-tapi-mcp/SKILL.md`:

1. `gradle_get_build_environment` for resolved Gradle/Java versions
2. `gradle_get_project_overview` for module hierarchy
3. `gradle_run_tasks` with `["build"]` or targeted `:plugin:test` when verification is needed

### GitHub and pull requests (Cursor Cloud)

Do not rely on bare `gh` commands without checking availability. `.cursor/install.sh` symlinks
`/exec-daemon/gh` into `~/.local/bin/gh` and, when `/usr/local/bin` is writable, into
`/usr/local/bin/gh`. When `gh auth status` is unauthenticated, it logs in with `GH_TOKEN` or
`GITHUB_TOKEN` if set.

| Goal | Preferred approach |
|------|-------------------|
| Create or update a PR | Built-in **ManagePullRequest** tool (`create_pr` / `update_pr`) |
| Edit PR labels | **EditPullRequestLabels** tool |
| Verify changes locally | `./gradlew build` or Gradle MCP `gradle_run_tasks` |
| PR check status / CI logs | `gh` only after `gh auth status` succeeds (see `.cursor/skills/cloud-github/SKILL.md`) |

If `gh` is not found or auth fails, use ManagePullRequest for PR work and Gradle for build
verification instead of retrying `gh`. Set `GH_TOKEN` in Cursor Cloud Secrets when the GitHub App
token lacks required scopes.

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
