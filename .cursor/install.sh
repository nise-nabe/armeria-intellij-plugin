#!/usr/bin/env bash
set -euo pipefail

readonly GRADLE_TAPI_MCP_VERSION="0.2.2"
readonly INSTALL_DIR="${HOME}/.local/share/gradle-tapi-mcp-server"
readonly JAR_NAME="gradle-tapi-mcp-server-${GRADLE_TAPI_MCP_VERSION}.jar"
readonly JAR_PATH="${INSTALL_DIR}/${JAR_NAME}"

if [[ ! -f "${JAR_PATH}" ]]; then
  mkdir -p "${INSTALL_DIR}"
  curl -fsSL -o "${JAR_PATH}" \
    "https://github.com/nise-nabe/gradle-tapi-mcp-server/releases/download/v${GRADLE_TAPI_MCP_VERSION}/${JAR_NAME}"
fi

./gradlew --no-daemon build
