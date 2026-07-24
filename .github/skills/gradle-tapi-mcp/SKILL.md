---
name: gradle-tapi-mcp
description: >-
  Use the gradle MCP server for all Gradle task execution and build verification
  in this repo. Prefer MCP over shell ./gradlew. Configured in .github/mcp.json;
  JAR installed by copilot-setup-steps or .github/scripts/install-gradle-tapi-mcp.sh.
---

# Gradle Tooling API MCP (Copilot / GitHub Agents)

This repository configures [nise-nabe/gradle-tapi-mcp-server](https://github.com/nise-nabe/gradle-tapi-mcp-server) v0.5.1:

| Environment | Config | Install |
|-------------|--------|---------|
| GitHub Copilot CLI / cloud agent | `.github/mcp.json` | `.github/workflows/copilot-setup-steps.yml` or `.github/scripts/install-gradle-tapi-mcp.sh` |
| Cursor Cloud Agents | `.cursor/mcp.json` | `.cursor/install.sh` |

The wrapper `.github/scripts/gradle-mcp-server.sh` sets `GRADLE_PROJECT_DIR` to the git root before starting the MCP server.

**Use MCP for all Gradle tasks.** Fall back to shell `./gradlew` only when MCP is unresponsive or returns `BUILD_ALREADY_RUNNING` that cannot be cancelled.

## Workflow

1. `gradle_connection_status` — confirm `connectedAny: true`; if not, `gradle_connect` with the repository root
2. `gradle_get_build_environment` — resolved Gradle/Java versions
3. `gradle_get_project_overview` — module hierarchy (`plugin-shared`, `plugin-route-analysis`, `plugin-wizard`, `plugin`)
4. `gradle_run_tasks` / `gradle_run_tests` for verification

Use `background: true` and poll `gradle_get_build_status` for runs that may exceed ~30s (`build`, `:plugin:test`, `:plugin-route-analysis:test`, cold start).

## Concurrency

Only **one** MCP build per `projectDirectory` at a time (gate releases immediately on terminal status). Batch multiple test classes/methods in a **single** `gradle_run_tests` instead of parallel MCP calls. To run both `:test` and a custom `JvmTestSuite` (`fastTest`) in one build, use `tasks: [":mod:test", ":mod:fastTest"]` + `includePatterns`. Use `gradle_cancel_build` + poll when you need to stop a stale run (`not_running` means the build already finished).

Do **not** run MCP `gradle_run_tests` and shell `./gradlew :plugin:test` concurrently (IntelliJ test sandbox contention). In this multi-project repo, `testClasses`/`testMethods` require `taskPath` or `tasks` (e.g. `taskPath: ":plugin:test"`).

## Common tasks (this repo)

| Goal | MCP |
|------|-----|
| Compile | `gradle_run_tasks` `{ "tasks": [":plugin:compileKotlin", ":plugin:compileTestKotlin"] }` |
| One or more test classes/methods | `gradle_run_tests` `{ "taskPath": ":plugin-route-analysis:test", "testMethods": { "FQCN": ["method"] }, "background": true }` — batch in one call |
| Plugin fixture tests | `gradle_run_tasks` `{ "tasks": [":plugin:test"], "background": true }` |
| Route-analysis fixture tests | `gradle_run_tasks` `{ "tasks": [":plugin-route-analysis:test"], "background": true }` |
| Fast unit tests | `gradle_run_tasks` `{ "tasks": [":plugin-route-analysis:fastTest"], "background": true }` |
| Lint Kotlin (when Kotlin is staged) | `gradle_run_tasks` `{ "tasks": ["ktlintCheck"], "background": true }` — fix with `ktlintFormat` or manual edits, then re-check; shell fallback: `./gradlew ktlintCheck` |
| Full verify | `gradle_run_tasks` `{ "tasks": ["build"], "background": true }` |

When `git diff --cached --name-only -- '*.kt' '*.kts' '.editorconfig'` is non-empty, coding agents must pass `ktlintCheck` before `git commit` (`gradle_run_tasks` `["ktlintCheck"]`, `background: true` + poll `gradle_get_build_status` until terminal success; fix with `ktlintFormat` or manual edits and re-check). Wait for any in-flight MCP build to finish or cancel it first. Omit ktlint when the staged index contains none of those paths.

### Recommended agent workflow

1. `gradle_connection_status` — confirm MCP is connected.
2. `gradle_run_tasks` with `[":plugin:compileKotlin", ":plugin:compileTestKotlin"]` (foreground if warm, else `background: true` + poll).
3. Before each `git commit` when staged Kotlin or `.editorconfig` is present (see detection command above), run `gradle_run_tasks` with `["ktlintCheck"]` (`background: true` + poll). On failure, apply `ktlintFormat` or manual fixes and re-run until clean.
4. Verify tests via MCP (`gradle_run_tests` or `gradle_run_tasks` with `background: true` + poll).
5. Before opening a PR, run `gradle_run_tasks` with `["build"]` and `background: true`, poll to completion.

If MCP is unresponsive: `gradle_list_builds` or poll `gradle_get_build_status` with the `buildId` (reconciles disk records automatically), then shell fallback.

Full reference: `.cursor/skills/gradle-tapi-mcp/SKILL.md`
