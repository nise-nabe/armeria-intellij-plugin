# Agent instructions

## Cursor Cloud specific instructions

IntelliJ Platform plugin for Armeria. Gradle multi-project build (`build-logic`, `plugin-shared`, `plugin-route-analysis`, `plugin-wizard`, `plugin`). No web UI or Docker services.

### Prerequisites

- **Cursor Cloud setup**: `mise.toml` pins Java 25 and defines the `cloud:install` task (gh CLI setup and IntelliJ Platform warm). `.cursor/install.sh` bootstraps mise when needed, installs the Gradle MCP JAR first (no Java dependency), then runs `mise run cloud:install`. Locally, reproduce the same steps with `mise run cloud:install` after `mise install`.
- **Gradle daemon JVM**: Adoptium 25 (pinned in `gradle/gradle-daemon-jvm.properties`; Foojay resolver downloads it). The running daemon may report a different Java until it restarts and applies the pin — see `gradle_get_build_environment`.
- **Compile toolchain**: Java 21 JetBrains (configured in `build-logic/src/main/kotlin/com.linecorp.intellij.platform-plugin.gradle.kts` and inherited by `plugin/`).
- Gradle wrapper (`./gradlew`) remains available as a **fallback** when MCP is unavailable.
- **IntelliJ Platform prefetch**: `cloud:install` runs `compileTestKotlin` on plugin modules so IPGP downloads Ultimate into the Gradle cache. Cursor checkpoints that disk state after a long `install`, so later cloud agents usually skip the cold IDE download. Bumping `idea-platform` in `gradle/libs.versions.toml` triggers a fresh download on the next install.

### MCP: Gradle Tooling API (default for Gradle tasks)

The `gradle` MCP server (`nise-nabe/gradle-tapi-mcp-server` v0.5.1) is configured in `.cursor/mcp.json`. The install script downloads the release JAR to `~/.local/share/gradle-tapi-mcp-server/`, verifies its SHA-256, and exposes it via a stable `gradle-tapi-mcp-server.jar` symlink. `GRADLE_PROJECT_DIR` is set to the workspace root.

**Use MCP for all Gradle task execution and verification** unless MCP is unresponsive or returns `BUILD_ALREADY_RUNNING` that cannot be cancelled. See `.cursor/rules/gradle-mcp.mdc` and `.cursor/skills/gradle-tapi-mcp/SKILL.md`.

Default workflow:

1. `gradle_connection_status` — confirm connected (`connectedAny: true`)
2. `gradle_get_build_environment` for resolved Gradle/Java versions
3. `gradle_get_project_overview` for module hierarchy
4. `gradle_run_tasks` / `gradle_run_tests` for verification (`background: true` + poll `gradle_get_build_status` when a run may exceed ~30s)

Shell `./gradlew` is fallback only: MCP server unresponsive, or final CI parity before merge. Do not run MCP `gradle_run_tests` and shell `./gradlew :plugin:test` at the same time (sandbox contention). If MCP stops responding, poll with `gradle_get_build_status` or `gradle_list_builds` (reconciles disk records), then fall back to shell. Task discovery: `gradle_get_build_invocations` / `gradle_get_project_model` (see `gradle-tapi-mcp` skill).

### GitHub and pull requests (Cursor Cloud)

Do not rely on bare `gh` commands without checking availability. `.cursor/install.sh` symlinks
`/exec-daemon/gh` into `~/.local/bin/gh` and, when `/usr/local/bin` is writable, into
`/usr/local/bin/gh`. When `gh auth status` is unauthenticated, it logs in with `GH_TOKEN` or
`GITHUB_TOKEN` if set.

| Goal | Preferred approach |
|------|-------------------|
| Create or update a PR | Built-in **ManagePullRequest** tool (`create_pr` / `update_pr`); body format in `.cursor/rules/pr-description-format.mdc` |
| Edit PR labels | **EditPullRequestLabels** tool |
| Verify changes locally | **Gradle MCP** (`gradle_run_tasks` / `gradle_run_tests`); shell `./gradlew build` for CI parity fallback — see `gradle-tapi-mcp` skill |
| PR check status / CI logs | `gh` only after `gh auth status` succeeds (see `.cursor/skills/cloud-github/SKILL.md`) |

If `gh` is not found or auth fails, use ManagePullRequest for PR work and Gradle MCP for build
verification instead of retrying `gh`. Set `GH_TOKEN` in Cursor Cloud Secrets when the GitHub App
token lacks required scopes.

### Plugin releases

GitHub Releases distribution is documented in `.cursor/skills/release/SKILL.md`. Version lives in
`gradle.properties` (`pluginVersion`); changelog in `plugin/CHANGELOG.md`. There is no automated
release workflow — tag and `gh release create` after merging a version-bump PR to `main`.

### Build, test, lint

Prefer **Gradle MCP** for the tasks below. Use `background: true` and poll `gradle_get_build_status` for long runs. Fall back to shell `./gradlew` only when MCP is unavailable.

| Goal | MCP (preferred) | Shell fallback |
|------|---------------|----------------|
| Full verify | `gradle_run_tasks` `["build"]` + background/poll | `./gradlew build` |
| Lint Kotlin | `gradle_run_tasks` `["ktlintCheck"]` + background/poll | `./gradlew ktlintCheck` |
| Format Kotlin | `gradle_run_tasks` `["ktlintFormat"]` + background/poll | `./gradlew ktlintFormat` |
| Compile plugin | `gradle_run_tasks` `[":plugin:compileKotlin"]` | `./gradlew :plugin:compileKotlin` |
| Plugin fixture tests | `gradle_run_tasks` `[":plugin:test"]` or `gradle_run_tests` per class + background/poll | `./gradlew :plugin:test` |
| Route-analysis fixture tests | `gradle_run_tasks` `[":plugin-route-analysis:test"]` + background/poll | `./gradlew :plugin-route-analysis:test` |
| Pure unit tests (`plugin-route-analysis/src/fastTest`) | `gradle_run_tasks` `[":plugin-route-analysis:fastTest"]` + background/poll | `./gradlew :plugin-route-analysis:fastTest` |
| All route-analysis tests (fixture + fast) | `gradle_run_tasks` `[":plugin-route-analysis:check"]` + background/poll | `./gradlew :plugin-route-analysis:check` |
| Run IDE sandbox | `gradle_run_tasks` `[":plugin:runIde"]` | `./gradlew :plugin:runIde` |
| Fix stale test sandbox | — | `.cursor/clean-test-sandbox.sh` |

`:plugin:test` and `:plugin-route-analysis:test` run platform PSI fixture tests under each module's `src/test`. `:plugin-route-analysis:fastTest` runs pure unit tests under `plugin-route-analysis/src/fastTest` (still on the IntelliJ Platform test runtime, but without PSI fixtures). Use `build` to run the full suite across modules.

Kotlin style is enforced by ktlint (`com.linecorp.intellij.ktlint` convention, `ktlint_official`). `ktlintCheck` is part of `check` / `build`.

### Project layout

| Path | Role |
|------|------|
| `plugin-shared/` | Shared bundle, icons, and starters used by other modules |
| `plugin-route-analysis/` | Route Explorer, inspections, and related analysis (`src/test`, `src/fastTest`) |
| `plugin-wizard/` | New Project Wizard templates and verification |
| `plugin/` | Aggregating plugin module, run config, Clients explorer, resources, `CHANGELOG.md` |
| `build-logic/` | Shared IntelliJ Platform Gradle conventions |
| `gradle/libs.versions.toml` | Version pins (Kotlin, IPGP, IDEA platform) |

Main Kotlin packages live under each module's `src/main/kotlin/com/linecorp/intellij/plugins/armeria/`. User-visible strings go through `message(...)` and `ArmeriaBundle.properties`. CI (`.github/workflows/main.yml`) runs per-module test tasks (`:plugin-wizard:test`, `:plugin-route-analysis:fastTest`, `:plugin-route-analysis:test`, `:plugin:test`) then `./gradlew build -x test` on Java 25.

### Running the plugin (headless cloud caveat)

`:plugin:runIde` launches **IntelliJ IDEA Ultimate** (`plugin/build.gradle.kts`) on the VNC display (`DISPLAY=:1`). In the offline cloud sandbox it hangs during startup awaiting `LicensingFacade` (AI-promo / Ultimate licensing) and never reaches the Welcome screen without a JetBrains license and network to JetBrains services. Disabling `org.jetbrains.completion.full.line` in the sandbox `config/disabled_plugins.txt` clears one promo stall but startup still blocks. For headless verification, exercise the plugin through the platform test suites (they run the real plugin code, e.g. `ArmeriaRouteCollector.collect` discovering routes from Java/Kotlin PSI) instead of the GUI.
