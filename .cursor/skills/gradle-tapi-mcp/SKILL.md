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

Avoid `includeTasks=true` and heavy model queries unless necessary. `gradle_run_tasks` omits stdout/stderr by default (`includeOutput=false`).

## Upstream documentation

Full tool reference and advanced workflows live in the upstream repository:

- [README (v0.2.3)](https://github.com/nise-nabe/gradle-tapi-mcp-server/blob/v0.2.3/README.md)
- [gradle-tapi-mcp skill](https://github.com/nise-nabe/gradle-tapi-mcp-server/tree/v0.2.3/skills/gradle-tapi-mcp)
