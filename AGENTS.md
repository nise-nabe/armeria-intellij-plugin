# Agent instructions

## Cursor Cloud specific instructions

IntelliJ Platform plugin for Armeria. Gradle multi-project build (`build-logic` + `plugin`). No web UI or Docker services.

### Prerequisites

- **Gradle daemon JVM**: Adoptium 25 (pinned in `gradle/gradle-daemon-jvm.properties`; Foojay resolver downloads it). The running daemon may report a different Java until it restarts and applies the pin — see `gradle_get_build_environment`.
- **Compile toolchain**: Java 21 JetBrains (configured in `build-logic/src/main/kotlin/com.linecorp.intellij.platform-plugin.gradle.kts` and inherited by `plugin/`).
- Gradle wrapper (`./gradlew`) downloads the distribution and dependencies on first use.

### MCP: Gradle Tooling API

The `gradle` MCP server (`nise-nabe/gradle-tapi-mcp-server` v0.3.3) is configured in `.cursor/mcp.json`. The install script downloads the release JAR to `~/.local/share/gradle-tapi-mcp-server/`, verifies its SHA-256, and exposes it via a stable `gradle-tapi-mcp-server.jar` symlink. `GRADLE_PROJECT_DIR` is set to the workspace root.

Prefer token-efficient MCP workflows documented in `.cursor/skills/gradle-tapi-mcp/SKILL.md`:

1. `gradle_get_build_environment` for resolved Gradle/Java versions
2. `gradle_get_project_overview` for module hierarchy
3. `gradle_run_tasks` with `[":plugin:compileKotlin"]` for fast compile checks

For **`:plugin:test` and `build`**, prefer `./gradlew` in Cursor Cloud — IntelliJ tests are long-running and MCP clients often time out (~60s). Use `background: true` and poll `gradle_get_build_status` for MCP builds; the server rejects a second MCP build on the same project with `BUILD_ALREADY_RUNNING`. Do not run MCP `gradle_run_tests` and shell `./gradlew :plugin:test` at the same time (sandbox contention). Cold-start compile may also timeout in foreground while Gradle still succeeds — use background + poll or `gradle_list_builds`. If MCP stops responding, poll with `gradle_get_build_status` (merges disk progress from `.gradle/mcp-builds/<buildId>/`) or read `mcp-result.json` and fall back to shell. Task discovery: see `gradle-tapi-mcp` skill (`gradle_get_build_invocations` / `gradle_get_project_model`).

### GitHub and pull requests (Cursor Cloud)

Do not rely on bare `gh` commands without checking availability. `.cursor/install.sh` symlinks
`/exec-daemon/gh` into `~/.local/bin/gh` and, when `/usr/local/bin` is writable, into
`/usr/local/bin/gh`. When `gh auth status` is unauthenticated, it logs in with `GH_TOKEN` or
`GITHUB_TOKEN` if set.

| Goal | Preferred approach |
|------|-------------------|
| Create or update a PR | Built-in **ManagePullRequest** tool (`create_pr` / `update_pr`); body format in `.cursor/rules/pr-description-format.mdc` |
| Edit PR labels | **EditPullRequestLabels** tool |
| Verify changes locally | `./gradlew build` (preferred for tests); MCP for compile checks — see `gradle-tapi-mcp` skill |
| PR check status / CI logs | `gh` only after `gh auth status` succeeds (see `.cursor/skills/cloud-github/SKILL.md`) |

If `gh` is not found or auth fails, use ManagePullRequest for PR work and Gradle for build
verification instead of retrying `gh`. Set `GH_TOKEN` in Cursor Cloud Secrets when the GitHub App
token lacks required scopes.

### Plugin releases

GitHub Releases distribution is documented in `.cursor/skills/release/SKILL.md`. Version lives in
`gradle.properties` (`pluginVersion`); changelog in `plugin/CHANGELOG.md`. There is no automated
release workflow — tag and `gh release create` after merging a version-bump PR to `main`.

### Build, test, lint

| Goal | Command |
|------|---------|
| Full verify | `./gradlew build` |
| Compile plugin | `./gradlew :plugin:compileKotlin` |
| Unit tests | `./gradlew :plugin:test` |
| Run IDE sandbox | `./gradlew :plugin:runIde` |
| Fast CI-style check | `./gradlew --no-daemon :plugin:compileKotlin :plugin:test` |
| Fix stale test sandbox | `.cursor/clean-test-sandbox.sh` |

There is no separate lint task; `./gradlew build` is the compile/test gate.

### Project layout

| Path | Role |
|------|------|
| `plugin/` | Plugin sources, resources, tests, `CHANGELOG.md` |
| `build-logic/` | Shared IntelliJ Platform Gradle conventions |
| `gradle/libs.versions.toml` | Version pins (Kotlin, IPGP, IDEA platform) |

Main Kotlin code: `plugin/src/main/kotlin/com/linecorp/intellij/plugins/armeria/`. User-visible strings go through `message(...)` and `ArmeriaBundle.properties`.
