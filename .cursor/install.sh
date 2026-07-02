#!/usr/bin/env bash
set -euo pipefail

readonly GRADLE_TAPI_MCP_VERSION="0.2.2"
readonly GRADLE_TAPI_MCP_SHA256="4eeab95da2e3582df1c9879a48a638bd341a4e0e29113ee01199b7e9b93cdab1"
readonly INSTALL_DIR="${HOME}/.local/share/gradle-tapi-mcp-server"
readonly VERSIONED_JAR_NAME="gradle-tapi-mcp-server-${GRADLE_TAPI_MCP_VERSION}.jar"
readonly VERSIONED_JAR_PATH="${INSTALL_DIR}/${VERSIONED_JAR_NAME}"
readonly STABLE_JAR_PATH="${INSTALL_DIR}/gradle-tapi-mcp-server.jar"

verify_jar_sha256() {
  local jar_path="$1"
  local actual
  actual="$(sha256sum "${jar_path}" | awk '{print $1}')"
  if [[ "${actual}" != "${GRADLE_TAPI_MCP_SHA256}" ]]; then
    echo "SHA-256 mismatch for ${jar_path}" >&2
    echo "Expected: ${GRADLE_TAPI_MCP_SHA256}" >&2
    echo "Actual:   ${actual}" >&2
    return 1
  fi
}

if [[ ! -f "${VERSIONED_JAR_PATH}" ]]; then
  mkdir -p "${INSTALL_DIR}"
  curl -fsSL -o "${VERSIONED_JAR_PATH}" \
    "https://github.com/nise-nabe/gradle-tapi-mcp-server/releases/download/v${GRADLE_TAPI_MCP_VERSION}/${VERSIONED_JAR_NAME}"
fi

verify_jar_sha256 "${VERSIONED_JAR_PATH}"
ln -sfn "${VERSIONED_JAR_NAME}" "${STABLE_JAR_PATH}"

./gradlew --no-daemon build
