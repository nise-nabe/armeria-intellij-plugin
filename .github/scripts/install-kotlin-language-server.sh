#!/usr/bin/env bash
set -euo pipefail

readonly KOTLIN_LS_VERSION="1.3.13"
readonly KOTLIN_LS_SHA256="4fe7d71d087b307c7869036171bd9d8c6a4284cd7c25b89098b0a24eb2d9b6d2"
readonly INSTALL_DIR="${HOME}/.local/share/kotlin-language-server"
readonly VERSIONED_DIR="${INSTALL_DIR}/${KOTLIN_LS_VERSION}"
readonly BIN_PATH="${HOME}/.local/bin/kotlin-language-server"
readonly SERVER_ZIP="${INSTALL_DIR}/server-${KOTLIN_LS_VERSION}.zip"
readonly MAX_DOWNLOAD_ATTEMPTS=2

verify_zip_sha256() {
  local zip_path="$1"
  local actual
  actual="$(sha256sum "${zip_path}" | awk '{print $1}')"
  if [[ "${actual}" != "${KOTLIN_LS_SHA256}" ]]; then
    echo "SHA-256 mismatch for ${zip_path}" >&2
    echo "Expected: ${KOTLIN_LS_SHA256}" >&2
    echo "Actual:   ${actual}" >&2
    return 1
  fi
}

download_server_zip() {
  mkdir -p "${INSTALL_DIR}"
  local tmp
  tmp="$(mktemp "${INSTALL_DIR}/.server-${KOTLIN_LS_VERSION}.XXXXXX.zip")"

  if ! curl -fsSL -o "${tmp}" \
    "https://github.com/fwcd/kotlin-language-server/releases/download/${KOTLIN_LS_VERSION}/server.zip"; then
    rm -f "${tmp}"
    echo "curl failed to download kotlin-language-server ${KOTLIN_LS_VERSION}" >&2
    return 1
  fi

  mv -f "${tmp}" "${SERVER_ZIP}"
}

extract_server() {
  rm -rf "${VERSIONED_DIR}"
  mkdir -p "${VERSIONED_DIR}"
  unzip -q "${SERVER_ZIP}" -d "${VERSIONED_DIR}"
  if [[ ! -x "${VERSIONED_DIR}/server/bin/kotlin-language-server" ]]; then
    echo "kotlin-language-server binary not found after extraction" >&2
    return 1
  fi
}

remove_install_artifacts() {
  rm -f "${SERVER_ZIP}"
  rm -rf "${VERSIONED_DIR}"
  rm -f "${BIN_PATH}"
}

ensure_server() {
  if [[ -x "${VERSIONED_DIR}/server/bin/kotlin-language-server" ]]; then
    return 0
  fi

  if [[ -f "${SERVER_ZIP}" ]] && ! verify_zip_sha256 "${SERVER_ZIP}"; then
    echo "Removing corrupted kotlin-language-server archive for re-download..." >&2
    rm -f "${SERVER_ZIP}"
  fi

  local attempt
  for attempt in $(seq 1 "${MAX_DOWNLOAD_ATTEMPTS}"); do
    if [[ ! -f "${SERVER_ZIP}" ]]; then
      download_server_zip || continue
    fi
    if verify_zip_sha256 "${SERVER_ZIP}" && extract_server; then
      return 0
    fi
    echo "Install attempt ${attempt}/${MAX_DOWNLOAD_ATTEMPTS} failed." >&2
    remove_install_artifacts
  done

  echo "Failed to install kotlin-language-server after ${MAX_DOWNLOAD_ATTEMPTS} attempts." >&2
  return 1
}

ensure_server
mkdir -p "${HOME}/.local/bin"
ln -sfn "${VERSIONED_DIR}/server/bin/kotlin-language-server" "${BIN_PATH}"
