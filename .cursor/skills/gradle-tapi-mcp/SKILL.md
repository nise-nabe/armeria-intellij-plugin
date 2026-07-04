---
name: gradle-tapi-mcp
description: >-
  Use the gradle MCP server for token-efficient build verification in this repo.
  Prefer lightweight Tooling API queries before running tasks.
---

# Gradle Tooling API MCP

This repository configures [nise-nabe/gradle-tapi-mcp-server](https://github.com/nise-nabe/gradle-tapi-mcp-server) v0.2.3 in `.cursor/mcp.json`. The JAR is installed by `.cursor/install.sh` to `~/.local/share/gradle-tapi-mcp-server/gradle-tapi-mcp-server.jar`. At MCP server launch, `.cursor/mcp.json` sets `GRADLE_PROJECT_DIR=${workspaceFolder}`.

## Workflow (token-efficient)

1. `gradle_connection_status` — confirm connected
2. `gradle_get_build_environment` — resolved Gradle/Java versions
3. `gradle_get_project_overview` — module hierarchy (`build-logic`, `plugin`)
4. `gradle_run_tasks` with `["build"]` or `[":plugin:test"]` when verification is needed

Avoid `includeTasks=true` and heavy model queries unless necessary. `gradle_run_tasks` omits stdout/stderr by default (`includeOutput=false`). On failure, check `failedTasks` and `buildSummary.failureSummary` before setting `includeOutput=true`.

## Repo-specific tips

### Verification commands

| Goal | MCP tool | Example |
|------|----------|---------|
| Full verify | `gradle_run_tasks` | `{ "tasks": ["build"] }` |
| All plugin tests | `gradle_run_tasks` | `{ "tasks": [":plugin:test"] }` |
| Single test class | `gradle_run_tests` | `{ "testClasses": ["com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteCollectorTest"] }` |
| Single test method | `gradle_run_tests` | `{ "testMethods": { "com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteCollectorTest": ["testCollectAnnotatedRoute"] } }` |

Prefer `gradle_run_tests` when iterating on a failing test; use `gradle_run_tasks` for full `:plugin:test` or `build`.

### JDK / toolchain debugging

This repo uses two JVM roles:

- **Gradle daemon JVM**: Adoptium 25 (`gradle/gradle-daemon-jvm.properties`)
- **Compile toolchain**: Java 21 JetBrains (`build-logic/src/main/kotlin/com.linecorp.intellij.platform-plugin.gradle.kts`)

`gradle_get_build_environment` shows the daemon's Java. For all detected JDKs (including toolchain downloads under `~/.gradle/jdks/`), use `gradle_get_java_runtimes`.

### IntelliJ test sandbox corruption

If `:plugin:test` fails with many unrelated test errors and a stack trace mentioning `PersistentEnumerator storage corrupted` under `.intellijPlatform/sandbox/plugin/`, the test sandbox index is stale — not an MCP or code regression.

```bash
rm -rf .intellijPlatform/sandbox/plugin/IU-*/system-test
```

Then rerun `:plugin:test` or `build` via MCP or `./gradlew`.

## Upstream documentation

Full tool reference and advanced workflows live in the upstream repository:

- [README (v0.2.3)](https://github.com/nise-nabe/gradle-tapi-mcp-server/blob/v0.2.3/README.md)
- [gradle-tapi-mcp skill](https://github.com/nise-nabe/gradle-tapi-mcp-server/tree/v0.2.3/skills/gradle-tapi-mcp)
