---
name: gradle-tapi-mcp
description: >-
  Use the gradle MCP server for all Gradle task execution and build verification
  in this repo. Prefer MCP over shell ./gradlew. Configured in .github/mcp.json;
  JAR installed by copilot-setup-steps or .github/scripts/install-gradle-tapi-mcp.sh.
---

# Gradle Tooling API MCP (Copilot / GitHub Agents)

This repository configures [nise-nabe/gradle-tapi-mcp-server](https://github.com/nise-nabe/gradle-tapi-mcp-server) v0.7.0:

| Environment | Config | Install |
|-------------|--------|---------|
| GitHub Copilot CLI / cloud agent | `.github/mcp.json` | `.github/workflows/copilot-setup-steps.yml` or `.github/scripts/install-gradle-tapi-mcp.sh` |
| Cursor Cloud Agents | `.cursor/mcp.json` | `.cursor/install.sh` |

The wrapper `.github/scripts/gradle-mcp-server.sh` sets `GRADLE_PROJECT_DIR` to the git root before starting the MCP server.

**Use MCP for all Gradle tasks.** Fall back to shell `./gradlew` only when MCP is unresponsive, returns `BUILD_ALREADY_RUNNING` that cannot be cancelled, or for CI parity — **not** to read compile/test output from a build MCP already ran (see **On MCP build failure** below).

## Workflow

1. `gradle_connection_status` — confirm `connectedAny: true`; if not, `gradle_connect` with the repository root
2. `gradle_get_build_environment` — resolved Gradle/Java versions
3. `gradle_get_project_overview` — module hierarchy (`plugin-shared`, `plugin-route-analysis`, `plugin-wizard`, `plugin`)
4. `gradle_run_tasks` / `gradle_run_tests` for verification

Use `background: true` **and** `queueIfBusy: true`, then poll `gradle_get_build_status` for runs that may exceed ~30s (`build`, `:plugin:test`, `:plugin-route-analysis:test`, cold start). `gradle_run_tests` requires selectors (`testClasses` / `testMethods` / `includePatterns`); a whole suite is `gradle_run_tasks`. Do **not** use `gradle_run_tests` with `taskPath: ":plugin:test"` (TestLauncher often reports the task cannot be found).

## On MCP build failure

Re-poll the **same `buildId`** — do **not** shell `./gradlew` just to read compiler or test output:

- Compile / task failure: `includeProblems: true` (also when `error` is the TAPI wrapper `Could not execute build using connection…` and `failedTasks` is non-empty). Default polls omit `problems`.
- Test failure: `includeTestDetails: true`
- Instant `Requested test task with path ':plugin:test' cannot be found`: retry `gradle_run_tasks` `{ "tasks": [":plugin:test"], "arguments": ["--tests", "FQCN"] }` — not shell
- Missing `gradle_run_tests` selectors: use `gradle_run_tasks` for the suite
- Still insufficient: `includeOutput: true`, or read `.gradle/mcp-builds/<buildId>/stdout.log` from `recordDirectory`

While `status: running`, use `waitUntilComplete: true` — do **not** `sleep` then poll.

## Concurrency

Only **one** MCP build per `projectDirectory` at a time (gate releases immediately on terminal status). `BUILD_ALREADY_RUNNING` includes `error.activeBuildId` for direct polling. Pass `queueIfBusy: true` with every `background: true` call when chaining compile → ktlint → test (queue depth max 3). Batch multiple test classes/methods in a **single** `gradle_run_tests` instead of parallel MCP calls. To run both `:test` and a custom `JvmTestSuite` (`fastTest`) in one build, use `tasks: [":mod:test", ":mod:fastTest"]` + `includePatterns`. Use `gradle_cancel_build` + poll when you need to stop a stale run (`not_running` means the build already finished).

Do **not** run MCP `gradle_run_tests` and shell `./gradlew :plugin:test` concurrently (IntelliJ test sandbox contention). In this multi-project repo, prefer explicit `taskPath` or `tasks` (e.g. `taskPath: ":plugin-route-analysis:test"`). Unscoped `testClasses`/`testMethods` auto-infer when unambiguous; otherwise `INVALID_ARGUMENT` includes `suggestedTaskPaths` and a `hint`. Selected `:plugin` tests: `gradle_run_tasks` `{ "tasks": [":plugin:test"], "arguments": ["--tests", "FQCN"] }` — not `gradle_run_tests`.

## Common tasks (this repo)

| Goal | MCP |
|------|-----|
| Compile | `gradle_run_tasks` `{ "tasks": [":plugin:compileKotlin", ":plugin:compileTestKotlin"] }` |
| One or more test classes/methods (route modules) | `gradle_run_tests` `{ "taskPath": ":plugin-route-analysis:test", "testMethods": { "FQCN": ["method"] }, "background": true, "queueIfBusy": true }` — batch in one call |
| Plugin fixture tests | `gradle_run_tasks` `{ "tasks": [":plugin:test"], "background": true, "queueIfBusy": true }` |
| Selected `:plugin` tests | `gradle_run_tasks` `{ "tasks": [":plugin:test"], "arguments": ["--tests", "FQCN"], "background": true, "queueIfBusy": true }` |
| Route-analysis fixture tests | `gradle_run_tasks` `{ "tasks": [":plugin-route-analysis:test"], "background": true, "queueIfBusy": true }` |
| Fast unit tests | `gradle_run_tasks` `{ "tasks": [":plugin-route-analysis:fastTest"], "background": true, "queueIfBusy": true }` |
| Lint Kotlin (when Kotlin/`.editorconfig` staged) | `gradle_run_tasks` `{ "tasks": ["ktlintCheck"], "background": true, "queueIfBusy": true }` — fix with `gradle_run_tasks` `["ktlintFormat"]` or manual edits, `git add`, then re-check; shell fallback: `./gradlew ktlintCheck` |
| Full verify | `gradle_run_tasks` `{ "tasks": ["build"], "background": true, "queueIfBusy": true }` |

When `git diff --cached --name-only -- '*.kt' '*.kts' '.editorconfig'` is non-empty, coding agents must pass `ktlintCheck` before `git commit` (`gradle_run_tasks` `["ktlintCheck"]`, `background: true`, `queueIfBusy: true` + poll `gradle_get_build_status` until terminal success; fix with `gradle_run_tasks` `["ktlintFormat"]` or manual edits, `git add` the changed files, and re-check). Wait for any in-flight MCP build to finish or cancel it (`gradle_cancel_build`) first. `ktlintFormat` is project-wide — re-stage only intended paths. Omit ktlint when the staged index contains none of those paths. Root `ktlintCheck` does not cover the `includeBuild("build-logic")` composite or `settings.gradle.kts`; when all staged Kotlin is in those locations, manually review style; when a commit mixes those paths with plugin-module Kotlin, manually review the `build-logic/` and `settings.gradle.kts` portions even if `ktlintCheck` passes — neither root `build` nor `compileKotlin` runs ktlint on those sources.

### Recommended agent workflow

1. `gradle_connection_status` — confirm MCP is connected.
2. `gradle_run_tasks` with `[":plugin:compileKotlin", ":plugin:compileTestKotlin"]` (foreground if warm, else `background: true`, `queueIfBusy: true` + poll).
3. Before each `git commit` when staged Kotlin or `.editorconfig` is present (see detection command above), run `gradle_run_tasks` with `["ktlintCheck"]` (`background: true`, `queueIfBusy: true` + poll). On failure, apply `gradle_run_tasks` `["ktlintFormat"]` or manual fixes, `git add` the changed files, and re-run until clean. Wait for in-flight MCP builds to finish or cancel them (`gradle_cancel_build`). `ktlintFormat` is project-wide — re-stage only intended paths. Root `ktlintCheck` does not cover `build-logic/` or `settings.gradle.kts`; when all staged Kotlin is in those locations, manually review style; when a commit mixes those paths with plugin-module Kotlin, manually review the `build-logic/` and `settings.gradle.kts` portions even if `ktlintCheck` passes.
4. Verify tests via MCP (`gradle_run_tests` with selectors on route modules, or `gradle_run_tasks` for `:plugin:test` / whole suites; `background: true`, `queueIfBusy: true` + poll). On `status: failed`, follow **On MCP build failure** — do not shell `./gradlew` for logs.
5. Before opening a PR, run `gradle_run_tasks` with `["build"]`, `background: true`, and `queueIfBusy: true`, poll to completion.

If MCP is unresponsive: `gradle_list_builds` or poll `gradle_get_build_status` with the `buildId` (reconciles disk records automatically), then shell fallback. Do not shell `./gradlew` to read errors from a completed MCP failure.

Full reference: `.cursor/skills/gradle-tapi-mcp/SKILL.md`
