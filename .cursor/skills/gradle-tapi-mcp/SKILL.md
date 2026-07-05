---
name: gradle-tapi-mcp
description: >-
  Use the gradle MCP server for token-efficient build verification in this repo.
  Prefer lightweight Tooling API queries before running tasks.
---

# Gradle Tooling API MCP

This repository configures [nise-nabe/gradle-tapi-mcp-server](https://github.com/nise-nabe/gradle-tapi-mcp-server) v0.3.2 in `.cursor/mcp.json`. The JAR is installed by `.cursor/install.sh` to `~/.local/share/gradle-tapi-mcp-server/gradle-tapi-mcp-server.jar`. At MCP server launch, `.cursor/mcp.json` sets `GRADLE_PROJECT_DIR=${workspaceFolder}`.

The MCP server may report `loading` for a few seconds on first use; call `gradle_connection_status` before other tools.

## Workflow (token-efficient)

1. `gradle_connection_status` — confirm connected (`connectedAny: true`)
2. `gradle_get_build_environment` — resolved Gradle/Java versions
3. `gradle_get_project_overview` — module hierarchy (`build-logic`, `plugin`)
4. `gradle_run_tasks` with `[":plugin:compileKotlin"]` when verification is needed

Avoid `includeTasks=true` and heavy model queries unless necessary. `gradle_run_tasks` omits stdout/stderr by default (`includeOutput=false`). On failure, check `failedTasks` and `buildSummary.failureSummary` before setting `includeOutput=true`.

### Inquiry tools (read-only, fast)

| Tool | Use |
|------|-----|
| `gradle_get_gradle_build` | Composite builds (`build-logic` included build) |
| `gradle_get_java_runtimes` | Daemon Java + toolchain JDKs under `~/.gradle/jdks/` |
| `gradle_get_build_cache_status` | Build cache / parallel / config-cache settings |
| `gradle_get_build_invocations` | Task discovery with `taskNamePrefix` / `taskGroup` filters |
| `gradle_get_project_model` | Task lists with `taskGroup` / `taskNamePrefix` / `maxTasks` |
| `gradle_get_project_publications` | Published artifacts |
| `gradle_list_builds` | Recent MCP builds (recovery when a call times out) |

### Task discovery (root vs `:plugin`)

The workspace root is a thin aggregator; most runnable tasks live on `:plugin` or in the `build-logic` composite build.

| Goal | Prefer |
|------|--------|
| Module tree / composite builds | `gradle_get_project_overview` or `gradle_get_gradle_build` |
| List verification tasks on `:plugin` | `gradle_get_project_model` with `taskGroup: "verification"` and `includeTasks: true` (tasks appear on the `:plugin` child node) |
| Find compile/test tasks | `gradle_get_build_invocations` with `taskNamePrefix: "compile"` and `includeTasks: true`, or use known paths (`:plugin:compileKotlin`, `:plugin:test`) |

There is no `projectPath` filter on model tools — they return the project tree; apply `taskGroup` / `taskNamePrefix` and read tasks from the `:plugin` child.

## When to use MCP vs shell

| Scenario | Prefer |
|----------|--------|
| Compile check (`:plugin:compileKotlin`), daemon warm | MCP `gradle_run_tasks` foreground — sub-second to ~2s, clean JSON |
| Compile check, cold start (first MCP build in session) | MCP `gradle_run_tasks` with `background: true` + poll, or shell — foreground often hits MCP client timeout (~60s) while Gradle still runs |
| Full `build` or `:plugin:test` in Cloud | **Shell** `./gradlew` — IntelliJ tests are long-running and MCP clients often time out (~60s) |
| Single test class (daemon + sandbox warm) | MCP `gradle_run_tests` with **one** class, `background: true` + polling — often ~5s |
| Single test class (cold sandbox / first run) | Expect several minutes; use MCP background + poll or shell — do not overlap with other test runs |
| Single test method | MCP `testClasses` may accept `ClassName.methodName` (Gradle `--tests` form); prefer one method at a time |
| MCP server unresponsive / all tools timeout | **Shell** `./gradlew`; Gradle daemon may still be IDLE while MCP is stuck |
| PR / CI parity check | `./gradlew build` or `./gradlew --no-daemon :plugin:compileKotlin :plugin:test` |

Do **not** start a second MCP `gradle_run_tasks` or `gradle_run_tests` while one is still running. Overlapping runs cause `InterruptedException`, can leave the IntelliJ test sandbox corrupted, and may wedge the MCP server until it is restarted.

Do **not** run MCP `gradle_run_tests` and shell `./gradlew :plugin:test` at the same time. Both spawn IntelliJ Platform test workers against the same sandbox and can appear hung for many minutes with no log output.

## Recovering from hung or stuck tests

If `:plugin:test` via MCP or shell shows no progress for several minutes (IDE sandbox cold start or overlapping runs):

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
   - MCP: a **single** `gradle_run_tests` with one `testClasses` entry, `background: true`, then poll `gradle_get_build_status`
   - Shell: `./gradlew :plugin:test --tests 'fully.qualified.ClassName'`

After the sandbox is warm, single-class MCP runs often finish in a few seconds. The first cold run can take several minutes.

## Running builds and tests

### Default: background + poll for anything that may exceed ~30s

MCP clients commonly time out around 60s. Gradle may finish successfully even when the initiating call returns `Request timed out`.

1. Start with `background: true`:

```json
{
  "tasks": [":plugin:compileKotlin"],
  "background": true
}
```

Or for a single test class:

```json
{
  "testClasses": ["com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteTreeBuilderTest"],
  "background": true
}
```

2. Poll `gradle_get_build_status` with the returned `buildId` until `status` is `succeeded`, `failed`, or `cancelled`.

3. Read `outcome` and `buildSummary` from the poll response. Use `includeProgress: true` for task/test events; set `includeOutput: true` only if you need truncated logs.

On failure, `stdout` often includes Kotlin/Java compiler diagnostics with file paths and line numbers — check there before enabling full logs.

**Recorder noise in `stderr`:** Builds may still report `succeeded` or `failed` correctly while `stderr` contains secondary errors from the MCP init-script recorder (for example `No such property: failure for class: DefaultTaskFailureResult` or `appendEvent()` missing on Gradle 9.6). Treat `status` / `outcome` / `buildSummary` as authoritative; see [gradle-tapi-mcp-server issues](https://github.com/nise-nabe/gradle-tapi-mcp-server/issues) for recorder fixes.

**If the start call times out:** run `gradle_list_builds` (if MCP responds) or `ls .gradle/mcp-builds/` and poll the most recent `buildId` with `gradle_get_build_status`.

**Foreground is fine when the daemon and outputs are warm** — e.g. repeated `:plugin:compileKotlin` after a recent build often completes in under 1s.

### Shell fallback (reliable in Cloud)

```bash
./gradlew :plugin:compileKotlin
./gradlew :plugin:test
# or a single class:
./gradlew :plugin:test --tests 'com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteTreeBuilderTest'
./gradlew build
```

### Disk recovery when MCP is stuck

Build records persist under `.gradle/mcp-builds/<buildId>/` even when the MCP server stops responding.

| File | Use |
|------|-----|
| `mcp-result.json` | Terminal MCP outcome when Gradle finished but MCP cannot answer |
| `gradle-result.json` | Authoritative Gradle init-script result when present |
| `stdout.log` / `stderr.log` | Full captured output after the build ends |
| `events.ndjson` | Task/test progress events |

If every MCP call times out but `./gradlew` still works:

1. List recent builds: `gradle_list_builds` (if MCP responds) or `ls .gradle/mcp-builds/`
2. Read `.gradle/mcp-builds/<buildId>/mcp-result.json` for `status`, `outcome`, and `buildSummary`
3. Avoid starting new MCP test runs until the environment recovers (restart the MCP server / agent session if needed)

## Repo-specific tips

### Verification commands

| Goal | MCP tool | Example |
|------|----------|---------|
| Full verify | shell (preferred) | `./gradlew build` |
| All plugin tests | shell (preferred) or MCP background | `./gradlew :plugin:test` |
| Single test class | `gradle_run_tests` + background, or shell | See running builds section |
| Fast compile gate | `gradle_run_tasks` | `{ "tasks": [":plugin:compileKotlin"] }` — use `background: true` on cold start |

Prefer shell for full `:plugin:test` and `build` in Cursor Cloud. Use MCP for lightweight queries, compile checks, and **one test class at a time** after compile passes.

### Recommended agent workflow (IntelliJ plugin changes)

1. `gradle_connection_status` — confirm MCP is connected.
2. `gradle_run_tasks` with `[":plugin:compileKotlin", ":plugin:compileTestKotlin"]` (foreground if warm, else `background: true` + poll).
3. For each new or changed test class, **sequentially**:
   - `gradle_run_tests` with one FQCN in `testClasses`, `background: true`
   - Poll `gradle_get_build_status` with `includeOutput: true` on failure
   - Do not start shell `./gradlew :plugin:test` until the MCP run finishes or is cancelled.
4. Before opening a PR, run `./gradlew build` in shell for CI parity (or at minimum `:plugin:test` if time allows).

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

## Upstream documentation

Full tool reference and advanced workflows live in the upstream repository:

- [README (v0.3.2)](https://github.com/nise-nabe/gradle-tapi-mcp-server/blob/v0.3.2/README.md)
- [gradle-tapi-mcp skill](https://github.com/nise-nabe/gradle-tapi-mcp-server/tree/v0.3.2/skills/gradle-tapi-mcp)
