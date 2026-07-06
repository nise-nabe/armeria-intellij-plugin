#!/usr/bin/env bash
set -euo pipefail

readonly INSTALL_DIR="${HOME}/.local/share/gradle-tapi-mcp-server"
readonly STABLE_JAR_PATH="${INSTALL_DIR}/gradle-tapi-mcp-server.jar"

if [[ ! -f "${STABLE_JAR_PATH}" ]]; then
  repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
  "${repo_root}/.github/scripts/install-gradle-tapi-mcp.sh"
fi

if [[ -z "${GRADLE_PROJECT_DIR:-}" ]]; then
  if git rev-parse --show-toplevel >/dev/null 2>&1; then
    export GRADLE_PROJECT_DIR="$(git rev-parse --show-toplevel)"
  else
    export GRADLE_PROJECT_DIR="$(pwd)"
  fi
fi

exec java -jar "${STABLE_JAR_PATH}"
