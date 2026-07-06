---
name: gradle-tapi-mcp
description: >-
  Use the gradle MCP server for all Gradle task execution and build verification
  in this repo. Prefer MCP over shell ./gradlew. Configured in .github/mcp.json;
  JAR installed by copilot-setup-steps or .github/scripts/install-gradle-tapi-mcp.sh.
---

# Gradle Tooling API MCP (Copilot / GitHub Agents)

This repository configures [nise-nabe/gradle-tapi-mcp-server](https://github.com/nise-nabe/gradle-tapi-mcp-server) v0.3.3:

| Environment | Config | Install |
|-------------|--------|---------|
| GitHub Copilot CLI / cloud agent | `.github/mcp.json` | `.github/workflows/copilot-setup-steps.yml` or `.github/scripts/install-gradle-tapi-mcp.sh` |
| Cursor Cloud Agents | `.cursor/mcp.json` | `.cursor/install.sh` |

The wrapper `.github/scripts/gradle-mcp-server.sh` sets `GRADLE_PROJECT_DIR` to the git root before starting the MCP server.

**Use MCP for all Gradle tasks.** Fall back to shell `./gradlew` only when MCP is unresponsive or returns `BUILD_ALREADY_RUNNING` that cannot be cancelled.

## Workflow

1. `gradle_connection_status` — confirm `connectedAny: true`; if not, `gradle_connect` with the repository root
2. `gradle_get_build_environment` — resolved Gradle/Java versions
3. `gradle_get_project_overview` — module hierarchy (`build-logic`, `plugin`)
4. `gradle_run_tasks` / `gradle_run_tests` for verification

Use `background: true` and poll `gradle_get_build_status` for runs that may exceed ~30s (`build`, `:plugin:test`, cold start).

## Common tasks (this repo)

| Goal | MCP |
|------|-----|
| Compile | `gradle_run_tasks` `{ "tasks": [":plugin:compileKotlin", ":plugin:compileTestKotlin"] }` |
| One test class | `gradle_run_tests` `{ "testClasses": ["fully.qualified.ClassName"], "background": true }` |
| All tests | `gradle_run_tasks` `{ "tasks": [":plugin:test"], "background": true }` |
| Full verify | `gradle_run_tasks` `{ "tasks": ["build"], "background": true }` |

## Constraints

- One MCP build per `projectDirectory` at a time (`BUILD_ALREADY_RUNNING` on overlap)
- Do **not** run MCP `gradle_run_tests` and shell `./gradlew :plugin:test` concurrently (IntelliJ test sandbox contention)
- On MCP timeout: `gradle_list_builds` or read `.gradle/mcp-builds/<buildId>/mcp-result.json`, then shell fallback

Full reference: `.cursor/skills/gradle-tapi-mcp/SKILL.md`
