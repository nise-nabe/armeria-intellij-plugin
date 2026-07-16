#!/usr/bin/env bash
set -euo pipefail

readonly GRADLE_TAPI_MCP_VERSION="0.5.1"
readonly GRADLE_TAPI_MCP_SHA256="b541460d4cc661c6b98342216631048b7cc517577f66caee9e3f1d6999b68741"
readonly INSTALL_DIR="${HOME}/.local/share/gradle-tapi-mcp-server"
readonly VERSIONED_JAR_NAME="gradle-tapi-mcp-server-${GRADLE_TAPI_MCP_VERSION}.jar"
readonly VERSIONED_JAR_PATH="${INSTALL_DIR}/${VERSIONED_JAR_NAME}"
readonly STABLE_JAR_PATH="${INSTALL_DIR}/gradle-tapi-mcp-server.jar"
readonly MAX_DOWNLOAD_ATTEMPTS=2

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

download_jar() {
  mkdir -p "${INSTALL_DIR}"
  local tmp
  tmp="$(mktemp "${INSTALL_DIR}/.${VERSIONED_JAR_NAME}.XXXXXX")"

  if ! curl -fsSL -o "${tmp}" \
    "https://github.com/nise-nabe/gradle-tapi-mcp-server/releases/download/v${GRADLE_TAPI_MCP_VERSION}/${VERSIONED_JAR_NAME}"; then
    rm -f "${tmp}"
    echo "curl failed to download ${VERSIONED_JAR_NAME}" >&2
    return 1
  fi

  mv -f "${tmp}" "${VERSIONED_JAR_PATH}"
}

remove_jar_artifacts() {
  rm -f "${VERSIONED_JAR_PATH}" "${STABLE_JAR_PATH}"
}

ensure_jar() {
  if [[ -f "${VERSIONED_JAR_PATH}" ]] && verify_jar_sha256 "${VERSIONED_JAR_PATH}"; then
    return 0
  fi

  if [[ -f "${VERSIONED_JAR_PATH}" ]]; then
    echo "Removing corrupted MCP server JAR for re-download..." >&2
  fi
  remove_jar_artifacts

  local attempt
  for attempt in $(seq 1 "${MAX_DOWNLOAD_ATTEMPTS}"); do
    if download_jar && verify_jar_sha256 "${VERSIONED_JAR_PATH}"; then
      return 0
    fi
    echo "Download attempt ${attempt}/${MAX_DOWNLOAD_ATTEMPTS} failed." >&2
    remove_jar_artifacts
  done

  echo "Failed to download a valid MCP server JAR after ${MAX_DOWNLOAD_ATTEMPTS} attempts." >&2
  return 1
}

ensure_jar
ln -sfn "${VERSIONED_JAR_NAME}" "${STABLE_JAR_PATH}"
