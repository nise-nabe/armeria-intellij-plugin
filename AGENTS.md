# Agent instructions

## Cursor Cloud specific instructions

IntelliJ Platform plugin for Armeria. Gradle multi-project build (`build-logic`, `plugin-shared`, `plugin-route-model`, `plugin-route-collectors`, `plugin-route-spring`, `plugin-route-protocol`, `plugin-route-analysis`, `plugin-wizard`, `plugin`). No web UI or Docker services.

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
| Route collectors fixture tests | `gradle_run_tasks` `[":plugin-route-collectors:test"]` + background/poll | `./gradlew :plugin-route-collectors:test` |
| Route spring fixture tests | `gradle_run_tasks` `[":plugin-route-spring:test"]` + background/poll | `./gradlew :plugin-route-spring:test` |
| Route protocol fixture tests | `gradle_run_tasks` `[":plugin-route-protocol:test"]` + background/poll | `./gradlew :plugin-route-protocol:test` |
| Route analysis fixture tests | `gradle_run_tasks` `[":plugin-route-analysis:test"]` + background/poll | `./gradlew :plugin-route-analysis:test` |
| Pure unit tests per module | `gradle_run_tasks` `[":plugin-route-<sub>:fastTest"]` + background/poll | `./gradlew :plugin-route-<sub>:fastTest` |
| All route tests (fixture + fast, every route module) | `gradle_run_tasks` `[":plugin-route-model:check", ":plugin-route-collectors:check", ":plugin-route-spring:check", ":plugin-route-protocol:check", ":plugin-route-analysis:check"]` + background/poll | `./gradlew :plugin-route-model:check :plugin-route-collectors:check :plugin-route-spring:check :plugin-route-protocol:check :plugin-route-analysis:check` |
| Run IDE sandbox | `gradle_run_tasks` `[":plugin:runIde"]` | `./gradlew :plugin:runIde` |
| Fix stale test sandbox | — | `.cursor/clean-test-sandbox.sh` |

`:plugin:test` and each `:plugin-route-*:test` run platform PSI fixture tests under their module's `src/test`. Each route submodule (collectors, spring, protocol, analysis) also has a `fastTest` suite under `src/fastTest` for pure unit tests that run on the IntelliJ Platform test runtime without PSI fixtures. Use `build` to run the full suite across all modules.

Kotlin style is enforced by ktlint (`com.linecorp.intellij.ktlint` convention, `ktlint_official`). `ktlintCheck` is part of `check` / `build`.

**Isolated Projects status**: all production modules (`plugin-route-model`, `plugin-route-collectors`, `plugin-route-spring`, `plugin-route-protocol`, `plugin-route-analysis`, `plugin`) compile cleanly under `-Dorg.gradle.unsafe.isolated-projects=true` (verified via `./gradlew compileKotlin -Dorg.gradle.unsafe.isolated-projects=true`). Enabling it as a CI default (e.g. in `gradle.properties`) is a follow-up once test-task configuration-time access is also confirmed; see the commented line in `gradle.properties`.

### Project layout

| Path | Role |
|------|------|
| `plugin-shared/` | Shared bundle, icons, and starters used by other modules |
| `plugin-route-model/` | Leaf module with `ArmeriaRoute`, `RouteMatch`, `RouteProtocol`, `DelegationKind`, etc. (no collector code) |
| `plugin-route-collectors/` | Core annotated + service-registration collectors, decorator/timeout/annotation helpers, support utilities, PSI traversal, `RouteContributor`/`RouteCollectContext` SPI, `ArmeriaKotlinRouteCollector`, and shared test fixtures |
| `plugin-route-spring/` | Spring MVC / Spring Boot / Spring config collectors and `ArmeriaDelegatedRouteCollector` |
| `plugin-route-protocol/` | GraphQL / gRPC / Thrift / IDL / proto-text collectors |
| `plugin-route-analysis/` | Route Explorer UI helpers, DocService support, navigation, duplicate index, and `ArmeriaRouteContributorBootstrap` (registers spring/protocol contributors into the collectors SPI registry) |
| `plugin-wizard/` | New Project Wizard templates and verification |
| `plugin/` | Aggregating plugin module, run config, Clients explorer, resources, `CHANGELOG.md` |
| `build-logic/` | Shared IntelliJ Platform Gradle conventions |
| `gradle/libs.versions.toml` | Version pins (Kotlin, IPGP, IDEA platform) |

Main Kotlin packages live under each module's `src/main/kotlin/com/linecorp/intellij/plugins/armeria/`. User-visible strings go through `message(...)` and `ArmeriaBundle.properties`. CI (`.github/workflows/main.yml`) runs per-module test tasks (`:plugin-wizard:test`, `:plugin-route-collectors:fastTest`/`test`, `:plugin-route-spring:fastTest`/`test`, `:plugin-route-protocol:fastTest`/`test`, `:plugin-route-analysis:fastTest`/`test`, `:plugin:test`) then `./gradlew build -x test` on Java 25.

### Running the plugin (headless cloud caveat)

`:plugin:runIde` launches **IntelliJ IDEA Ultimate** (`plugin/build.gradle.kts`) on the VNC display (`DISPLAY=:1`). In the offline cloud sandbox it hangs during startup awaiting `LicensingFacade` (AI-promo / Ultimate licensing) and never reaches the Welcome screen without a JetBrains license and network to JetBrains services. Disabling `org.jetbrains.completion.full.line` in the sandbox `config/disabled_plugins.txt` clears one promo stall but startup still blocks. For headless verification, exercise the plugin through the platform test suites (they run the real plugin code, e.g. `ArmeriaRouteCollector.collect` discovering routes from Java/Kotlin PSI) instead of the GUI.
