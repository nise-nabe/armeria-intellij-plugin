---
name: gradle-tapi-mcp
description: >-
  Use the gradle MCP server for token-efficient build verification in this repo.
  Prefer lightweight Tooling API queries before running tasks.
---

# Gradle Tooling API MCP

This repository configures [nise-nabe/gradle-tapi-mcp-server](https://github.com/nise-nabe/gradle-tapi-mcp-server) v0.3.0 in `.cursor/mcp.json`. The JAR is installed by `.cursor/install.sh` to `~/.local/share/gradle-tapi-mcp-server/gradle-tapi-mcp-server.jar`. At MCP server launch, `.cursor/mcp.json` sets `GRADLE_PROJECT_DIR=${workspaceFolder}`.

## Workflow (token-efficient)

1. `gradle_connection_status` — confirm connected
2. `gradle_get_build_environment` — resolved Gradle/Java versions
3. `gradle_get_project_overview` — module hierarchy (`build-logic`, `plugin`)
4. `gradle_run_tasks` with `["build"]` or `[":plugin:compileKotlin"]` when verification is needed

Avoid `includeTasks=true` and heavy model queries unless necessary. `gradle_run_tasks` omits stdout/stderr by default (`includeOutput=false`). On failure, check `failedTasks` and `buildSummary.failureSummary` before setting `includeOutput=true`.

### Task discovery (root vs `:plugin`)

The workspace root is a thin aggregator; most runnable tasks live on `:plugin` or in the `build-logic` composite build.

| Goal | Prefer |
|------|--------|
| Module tree / composite builds | `gradle_get_project_overview` or `gradle_get_gradle_build` |
| List verification tasks on `:plugin` | `gradle_get_project_model` with `projectPath: ":plugin"` and `taskGroup: "verification"` |
| Find compile/test tasks | Use `:plugin:` task paths (e.g. `:plugin:compileKotlin`, `:plugin:test`) |

`gradle_get_build_invocations` at the root with `taskNamePrefix: "compile"` often returns `[]`. Query `:plugin` via `projectPath` or use `gradle_get_project_model` instead.

## When to use MCP vs shell

| Scenario | Prefer |
|----------|--------|
| Compile check (`:plugin:compileKotlin`) | MCP `gradle_run_tasks` — fast (~15s), clean JSON |
| Full `build` or `:plugin:test` in Cloud | **Shell** `./gradlew` — IntelliJ tests are long-running and MCP clients often time out (~60s) |
| Single test class iteration (local, responsive MCP) | MCP `gradle_run_tests` with `background: true` + polling (see below) |
| MCP server unresponsive / all tools timeout | **Shell** `./gradlew`; Gradle daemon may still be IDLE while MCP is stuck |
| PR / CI parity check | `./gradlew build` or `./gradlew --no-daemon :plugin:compileKotlin :plugin:test` |

Do **not** start a second MCP `gradle_run_tasks` or `gradle_run_tests` while one is still running. Overlapping runs cause `InterruptedException`, can leave the IntelliJ test sandbox corrupted, and may wedge the MCP server until it is restarted.

## Long-running IntelliJ tests (`:plugin:test`, `gradle_run_tests`)

IntelliJ Platform plugin tests spin up IDE sandboxes and routinely exceed MCP client timeouts, even for a single test class.

### Recommended pattern (when MCP is responsive)

1. Start with `background: true`:

```json
{
  "tasks": [":plugin:test"],
  "background": true
}
```

Or for a single class:

```json
{
  "testClasses": ["com.linecorp.intellij.plugins.armeria.explorer.ArmeriaClientCollectorTest"],
  "background": true
}
```

2. Poll `gradle_get_build_status` with the returned `buildId` until `status` is `succeeded`, `failed`, or `cancelled`.

3. Read `outcome` and `buildSummary` from the poll response. Set `includeOutput: true` only if you need truncated logs.

**Cursor Cloud caveat:** `background: true` should return a `buildId` immediately, but MCP clients may still time out before the first response. If that happens, fall back to shell (below).

### Shell fallback (reliable in Cloud)

```bash
./gradlew :plugin:test
# or a single class:
./gradlew :plugin:test --tests 'com.linecorp.intellij.plugins.armeria.explorer.ArmeriaClientCollectorTest'
```

Use `./gradlew build` for full verification (same as CI).

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
| Full verify | `gradle_run_tasks` or shell | `{ "tasks": ["build"] }` — prefer `./gradlew build` in Cloud |
| All plugin tests | shell (preferred) or MCP background | `./gradlew :plugin:test` |
| Single test class | `gradle_run_tests` + background, or shell | See long-running section above |
| Fast compile gate | `gradle_run_tasks` | `{ "tasks": [":plugin:compileKotlin"] }` |

Prefer shell for `:plugin:test` and `build` in Cursor Cloud. Use MCP for lightweight queries and compile checks.

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

- [README (v0.3.0)](https://github.com/nise-nabe/gradle-tapi-mcp-server/blob/v0.3.0/README.md)
- [gradle-tapi-mcp skill](https://github.com/nise-nabe/gradle-tapi-mcp-server/tree/v0.3.0/skills/gradle-tapi-mcp)
