---
name: gradle-tapi-mcp
description: >-
  Use the gradle MCP server for all Gradle task execution and build verification
  in this repo. Prefer MCP over shell ./gradlew; use lightweight Tooling API
  queries before running tasks; batch multiple tests in one gradle_run_tests call.
---

# Gradle Tooling API MCP

This repository configures [nise-nabe/gradle-tapi-mcp-server](https://github.com/nise-nabe/gradle-tapi-mcp-server) v0.5.1 in `.cursor/mcp.json` (Cursor) and `.github/mcp.json` (Copilot). The JAR is installed by `.cursor/install.sh` or `.github/scripts/install-gradle-tapi-mcp.sh` to `~/.local/share/gradle-tapi-mcp-server/gradle-tapi-mcp-server.jar`. At MCP server launch, `GRADLE_PROJECT_DIR` is set to the workspace/git root.

The MCP server may report `loading` for a few seconds on first use; call `gradle_connection_status` before other tools.

For **declared** versions and build scripts, read `gradle/libs.versions.toml`, `settings.gradle.kts`, and module `build.gradle.kts` first. Use MCP for **resolved** Gradle/Java versions, module shape, and build/test execution.

## MCP tool discovery (token-efficient)

When using Cursor `mcp_get_tools`:

1. Call with **no arguments** for a catalog (names + short descriptions)
2. Call with `server` + `toolName` for the full schema immediately before invoking the tool
3. Avoid `server` only (all schemas at once) unless you need every parameter

## Workflow (token-efficient)

1. `gradle_connection_status` — confirm connected (`connectedAny: true`); if not, `gradle_connect` with the repository root. Use `refresh: true` when `runtimeStackAvailable` is false and you need resolved Gradle/Java versions without a separate `gradle_get_build_environment` call.
2. `gradle_get_build_environment` — resolved Gradle/Java versions (`versionInfo` on Gradle 9.4+)
3. `gradle_get_project_overview` — module hierarchy (`build-logic`, `plugin`)
4. `gradle_run_tasks` / `gradle_run_tests` when verification is needed

Avoid `includeTasks=true` and heavy model queries unless necessary. `gradle_run_tasks` omits stdout/stderr by default (`includeOutput=false`). On failure, check `failedTasks`, `buildSummary.failureSummary`, structured `testFailures` / `failedTestCount` (test runs), and structured `problems` before setting `includeOutput=true`.

### Inquiry tools (read-only, fast)

| Tool | Use |
|------|-----|
| `gradle_get_gradle_build` | Composite builds (`build-logic` included build) |
| `gradle_get_java_runtimes` | Daemon Java + toolchain JDKs under `~/.gradle/jdks/` |
| `gradle_get_build_cache_status` | Build cache / parallel / config-cache settings (`probeConfigurationCache` optional) |
| `gradle_get_help` | Gradle CLI help (`gradle --help` equivalent; Gradle 9.4+) |
| `gradle_get_build_invocations` | Task discovery with `taskNamePrefix` / `taskGroup` filters |
| `gradle_get_project_model` | Task lists with `taskGroup` / `taskNamePrefix` / `maxTasks` |
| `gradle_get_project_publications` | Published artifacts |
| `gradle_list_builds` | Recent MCP builds (recovery when a call times out; no TAPI required) |
| `gradle_cancel_build` | Cancel a background build; poll until status is no longer `running` |

`gradle_connect`, model/overview inquiry tools, and `gradle_get_build_cache_status` are rejected while an MCP build is running for the same `projectDirectory`. `gradle_disconnect` cancels active builds before closing the connection.

Do not call `gradle_get_project_model` and `gradle_get_build_invocations` back-to-back with no filters on large projects — token use explodes.

### Task discovery (root vs `:plugin`)

The workspace root is a thin aggregator; most runnable tasks live on `:plugin` or in the `build-logic` composite build.

| Goal | Prefer |
|------|--------|
| Module tree / composite builds | `gradle_get_project_overview` or `gradle_get_gradle_build` |
| List verification tasks on `:plugin` | `gradle_get_project_model` with `taskGroup: "verification"` and `includeTasks: true` (tasks appear on the `:plugin` child node) |
| Find compile/test tasks | `gradle_get_build_invocations` with `taskNamePrefix: "compile"` and `includeTasks: true`, or use known paths (`:plugin:compileKotlin`, `:plugin:test`) |

There is no `projectPath` filter on model tools — they return the project tree; apply `taskGroup` / `taskNamePrefix` and read tasks from the `:plugin` child.

## Test execution and concurrency

**Only one MCP build may run per `projectDirectory` at a time.** A second `gradle_run_tasks` or `gradle_run_tests` (even with `background: true`) returns `BUILD_ALREADY_RUNNING`. The gate releases as soon as the build reaches a terminal status in memory (no grace window).

| Goal | Approach |
|------|----------|
| Verify several changed tests | **Batch** them in **one** `gradle_run_tests` via `testMethods`, `testClasses`, or `includePatterns` |
| Verify both `:test` and custom `JvmTestSuite` (`fastTest`) | **One** `gradle_run_tests` with `tasks: [":mod:test", ":mod:fastTest"]` + `includePatterns` |
| Parallel MCP test calls on this repo | **Not supported** — wait for the current build or `gradle_cancel_build` first |
| Isolate a single failing class/method | One call per class/method; wait for terminal status before the next |
| Parallel tests across different repos | Each `projectDirectory` with `gradle_connect` + `background: true` (server pool limit) |

```json
{
  "taskPath": ":plugin:test",
  "testMethods": {
    "com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteTreeBuilderTest": ["buildRoot_groupsRoutesByModule"],
    "com.linecorp.intellij.plugins.armeria.other.OtherTest": ["testX"]
  },
  "background": true
}
```

Do **not** run MCP `gradle_run_tests` and shell `./gradlew :plugin:test` on the same checkout concurrently — IntelliJ Platform test workers compete for the same sandbox and can hang for many minutes.

In multi-project builds, `gradle_run_tests` with `testClasses` or `testMethods` requires `taskPath` or `tasks` to scope the test task (e.g. `taskPath: ":plugin:test"`). Unscoped class/method selection returns `INVALID_ARGUMENT`.

## When to use MCP vs shell

| Scenario | Prefer |
|----------|--------|
| Any Gradle task (default) | **MCP** `gradle_run_tasks` / `gradle_run_tests` |
| Compile check (`:plugin:compileKotlin`), daemon warm | MCP `gradle_run_tasks` foreground — sub-second to ~2s, clean JSON |
| Compile check, cold start (first MCP build in session) | MCP `gradle_run_tasks` with `background: true` + poll |
| Full `build` or `:plugin:test` | MCP with `background: true` + poll `gradle_get_build_status` |
| Single test class (daemon + sandbox warm) | MCP `gradle_run_tests` with **one** class and `taskPath: ":plugin:test"`, `background: true` + polling — often ~5s |
| Single test method | MCP `gradle_run_tests` with `taskPath: ":plugin:test"` and `testMethods: { "ClassName": ["methodName"] }`, or `testClasses: ["ClassName.methodName"]` (auto-normalized) |
| MCP server unresponsive / all tools timeout | **Shell** `./gradlew` after `gradle_list_builds` / disk recovery |
| PR / CI parity check (after MCP verify) | Shell `./gradlew build` when you need exact CI command parity |

On `BUILD_ALREADY_RUNNING`: poll `gradle_get_build_status`, `gradle_cancel_build` if the run is stale, or batch pending tests into one `gradle_run_tests` instead of starting another call.

## Recovering from hung or stuck tests

If `:plugin:test` via MCP or shell shows no progress for several minutes (IDE sandbox cold start or overlapping MCP + shell runs):

1. Stop test workers and Gradle test processes:

```bash
pkill -f "Gradle Test Executor" 2>/dev/null || true
pkill -f "gradle-wrapper.jar :plugin:test" 2>/dev/null || true
```

2. Clean the IntelliJ test sandbox:

```bash
.cursor/clean-test-sandbox.sh
# equivalent:
rm -rf .intellijPlatform/sandbox/plugin/IU-*/system-test
```

3. Retry **one** verification path only:
   - MCP: a **single** `gradle_run_tests` (batch multiple classes/methods when needed), `background: true`, then poll `gradle_get_build_status`
   - Shell: `./gradlew :plugin:test --tests 'fully.qualified.ClassName'`

After the sandbox is warm, single-class MCP runs often finish in a few seconds. The first cold run can take several minutes when the IDE distribution is not yet cached.

`.cursor/install.sh` prefetches Ultimate via `compileTestKotlin` so Cursor Cloud environment checkpoints can retain the IPGP/Gradle IDE cache across agents. Prefer relying on that snapshot for download cost; remaining cold time is mostly daemon + test sandbox warm-up.

## Running builds and tests

### Output defaults

`gradle_run_tasks` / `gradle_run_tests` return `outcome`, `buildSummary`, and failure metadata by default. `stdout` / `stderr` require `includeOutput=true` (`maxOutputChars` default 8000, `tailOutput` default true). Detailed `progress` requires `includeProgress=true`. Failed builds may include structured `problems` (label, details, severity, solutions) without enabling progress. Failed test runs include structured `testFailures` (class, method, exception, source line) and `failedTestCount` without `includeOutput`.

**Disk-only polling** (MCP restart or in-memory record evicted): `includeOutput=true` still returns empty streams until the build finishes and MCP writes `stdout.log` / `stderr.log`.

### Long or cold builds: background + poll (or rely on auto-detach)

Foreground builds auto-detach before the MCP client times out, returning a `buildId` you can poll. For cold starts (`build`, `:plugin:test`) or anything that may exceed ~30s, prefer explicit `background: true` so polling starts immediately.

1. Start with `background: true`:

```json
{
  "tasks": [":plugin:compileKotlin"],
  "background": true
}
```

Or for test selection:

```json
{
  "taskPath": ":plugin:test",
  "testMethods": {
    "com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteTreeBuilderTest": ["buildRoot_groupsRoutesByModule"]
  },
  "background": true
}
```

2. Poll `gradle_get_build_status` with the returned `buildId` until `status` is `succeeded`, `failed`, or `cancelled`. Prefer repeated short polls over one long wait. Optional `waitUntilComplete: true` uses a **server-side** wait only (`waitTimeoutMs` default 30s, max 60s); on timeout the build keeps running and the response includes `waitTimedOut`, `waitedMs`, and a `hint` to poll again. Non-wait status polls read memory/disk only and do not block on the Tooling API.

3. Read `outcome`, `buildSummary`, `failureCategory` (`TEST` / `GRADLE_TASK` / `TOOLING_CONNECTION` / `CANCELLED`), and `statusSource` (`memory` or `disk`) from the poll response. Use `includeProgress: true` for task/test events; set `includeOutput: true` only if you need truncated logs. For incremental log polling while running, pass `sinceStdoutOffset` / `sinceStderrOffset` to receive `stdoutDelta` / `stderrDelta` instead of re-reading prior prefixes.

`gradle_get_build_status` reconciles memory and disk records. While `status` is `running`, disk `events.ndjson` task events are merged into progress.

If you lose the `buildId`, use `gradle_list_builds` and poll the most recent entry.

**Foreground is fine when the daemon and outputs are warm** — e.g. repeated `:plugin:compileKotlin` after a recent build often completes in under 1s.

### Shell fallback (reliable in Cloud)

```bash
./gradlew :plugin:compileKotlin
./gradlew :plugin:test
# or a single class:
./gradlew :plugin:test --tests 'com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteTreeBuilderTest'
./gradlew build
```

### Disk recovery when MCP is unresponsive

Build records persist under `.gradle/mcp-builds/<buildId>/` even when the MCP server stops responding.

| File | Use |
|------|-----|
| `mcp-result.json` | Terminal MCP outcome (reconciled with `gradle-result.json`; includes `testFailures` when present) |
| `gradle-result.json` | Gradle init-script status while running |
| `stdout.log` / `stderr.log` | Full captured output after the build ends |
| `events.ndjson` | Task/test progress events |

If every MCP call times out but `./gradlew` still works:

1. List recent builds: `gradle_list_builds` (if MCP responds) or `ls .gradle/mcp-builds/`
2. Poll `gradle_get_build_status` with the `buildId`, or read `.gradle/mcp-builds/<buildId>/mcp-result.json` when MCP cannot answer at all
3. Avoid starting new MCP test runs until the environment recovers (restart the MCP server / agent session if needed)

## Repo-specific tips

### Verification commands

| Goal | MCP tool (preferred) | Shell fallback |
|------|---------------------|----------------|
| Full verify | `gradle_run_tasks` `{ "tasks": ["build"], "background": true }` | `./gradlew build` |
| Plugin fixture tests | `gradle_run_tasks` `{ "tasks": [":plugin:test"], "background": true }` | `./gradlew :plugin:test` |
| Route-analysis fixture tests | `gradle_run_tasks` `{ "tasks": [":plugin-route-analysis:test"], "background": true }` | `./gradlew :plugin-route-analysis:test` |
| Fast unit tests | `gradle_run_tasks` `{ "tasks": [":plugin-route-analysis:fastTest"], "background": true }` | `./gradlew :plugin-route-analysis:fastTest` |
| Route-analysis fixture + fast in one MCP build | `gradle_run_tests` `{ "tasks": [":plugin-route-analysis:test", ":plugin-route-analysis:fastTest"], "includePatterns": ["FQCN"], "background": true }` | `./gradlew :plugin-route-analysis:test :plugin-route-analysis:fastTest --tests 'FQCN'` |
| Route-analysis checks (fixture + fast) | `gradle_run_tasks` `{ "tasks": [":plugin-route-analysis:check"], "background": true }` | `./gradlew :plugin-route-analysis:check` |
| Single test class | `gradle_run_tests` `{ "taskPath": ":plugin-route-analysis:test", "testClasses": ["FQCN"], "background": true }` | `./gradlew :plugin-route-analysis:test --tests 'FQCN'` |
| Multiple test classes/methods | `gradle_run_tests` `{ "taskPath": ":plugin-route-analysis:test", "testMethods": { ... }, "background": true }` | `./gradlew :plugin-route-analysis:test --tests 'FQCN'` per class |
| Single test method | `gradle_run_tests` `{ "taskPath": ":plugin-route-analysis:test", "testMethods": { "FQCN": ["method"] }, "background": true }` | `./gradlew :plugin-route-analysis:test --tests 'FQCN.method'` |
| Fast compile gate | `gradle_run_tasks` `{ "tasks": [":plugin:compileKotlin"] }` | `./gradlew :plugin:compileKotlin` |
| Lint Kotlin (when Kotlin/`.editorconfig` staged) | `gradle_run_tasks` `{ "tasks": ["ktlintCheck"], "background": true }` | `./gradlew ktlintCheck` |

Prefer MCP for all verification. Use shell only when MCP is unresponsive or for final CI parity before merge.

### Recommended agent workflow (IntelliJ plugin changes)

1. `gradle_connection_status` — confirm MCP is connected.
2. `gradle_run_tasks` with `[":plugin:compileKotlin", ":plugin:compileTestKotlin"]` (foreground if warm, else `background: true` + poll).
3. Before each `git commit` when `git diff --cached --name-only -- '*.kt' '*.kts' '.editorconfig'` is non-empty, run `gradle_run_tasks` with `["ktlintCheck"]` (`background: true` + poll). On failure, apply `gradle_run_tasks` `["ktlintFormat"]` or manual fixes, `git add` the changed files, and re-run until clean. Wait for any in-flight MCP build to finish or cancel it (`gradle_cancel_build`) first. `ktlintFormat` is project-wide — re-stage only intended paths. Root `ktlintCheck` does not cover `build-logic/` or `settings.gradle.kts`; see `AGENTS.md` **Commit workflow (coding agents)**.
4. Verify tests via MCP (one build at a time on this repo):
   - Batch all changed classes/methods into **one** `gradle_run_tests` when doing a verification pass.
   - When isolating failures, run one class or method per call; wait for terminal status (or `gradle_cancel_build`) before the next.
   - Use `background: true`; on failure read `testFailures` / `buildSummary.failureSummary` first, then poll with `includeOutput: true` only if logs are still needed.
   - Do not overlap MCP runs with shell `./gradlew :plugin:test`.
5. Before opening a PR, run `gradle_run_tasks` with `["build"]` and `background: true`, poll to completion, then optionally shell `./gradlew build` for exact CI parity if MCP already passed.

### JDK / toolchain debugging

This repo uses two JVM roles:

- **Gradle daemon JVM (pinned)**: Adoptium 25 in `gradle/gradle-daemon-jvm.properties` (Foojay resolver downloads it on first use)
- **Compile toolchain**: Java 21 JetBrains (`build-logic/src/main/kotlin/com.linecorp.intellij.platform-plugin.gradle.kts`)

`gradle_get_build_environment` reports the **running daemon's** Java, which may differ from the pin until the daemon restarts and picks up Adoptium 25 (e.g. an existing daemon on Java 21 OpenJDK in Cloud). Do not treat a Java 21 daemon reading as a misconfiguration by itself — check whether a restart or `./gradlew --stop` is needed to apply the pin.

For all detected JDKs (including toolchain downloads under `~/.gradle/jdks/`), use `gradle_get_java_runtimes`.

### IntelliJ test sandbox corruption

If `:plugin:test` fails with many unrelated test errors and a stack trace mentioning `PersistentEnumerator storage corrupted` under `.intellijPlatform/sandbox/plugin/`, the test sandbox index is stale — not an MCP or code regression. This often follows an interrupted MCP test run.

```bash
.cursor/clean-test-sandbox.sh
# equivalent:
rm -rf .intellijPlatform/sandbox/plugin/IU-*/system-test
```

Then rerun `:plugin:test` or `build` via shell or MCP (background + polling).

## Troubleshooting

| Symptom | Action |
|---------|--------|
| `error.code: NOT_CONNECTED` | `gradle_connect` or restart the MCP server |
| `error.code: BUILD_ALREADY_RUNNING` | Poll `gradle_get_build_status`, `gradle_cancel_build` if stale (`not_running` = already finished), or batch tests into one `gradle_run_tests` |
| `error.code: INVALID_ARGUMENT` on `gradle_run_tests` | Add `taskPath` or `tasks` when using `testClasses`/`testMethods` in this multi-project repo |
| MCP call timed out but Gradle may still be running | Foreground runs auto-detach; use `gradle_list_builds` and poll `gradle_get_build_status` (short polls; do not rely on one long `waitUntilComplete`) |
| Huge MCP responses | Keep `includeTasks` / `includeTaskSelectors` false unless filtering |
| Declared Java vs daemon Java differ | Report both toolchain declaration (files) and daemon Java (MCP) |

## Upstream documentation

Full tool reference and advanced workflows live in the upstream repository:

- [README (v0.5.1)](https://github.com/nise-nabe/gradle-tapi-mcp-server/blob/v0.5.1/README.md)
- [gradle-tapi-mcp skill](https://github.com/nise-nabe/gradle-tapi-mcp-server/tree/main/skills/gradle-tapi-mcp)
- [Tool reference (reference.md)](https://github.com/nise-nabe/gradle-tapi-mcp-server/blob/main/skills/gradle-tapi-mcp/reference.md)
