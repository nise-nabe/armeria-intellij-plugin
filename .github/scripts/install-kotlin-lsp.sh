#!/usr/bin/env bash
set -euo pipefail

readonly KOTLIN_LSP_VERSION="262.8190.0"
readonly INSTALL_DIR="${HOME}/.local/share/kotlin-lsp"
readonly VERSIONED_DIR="${INSTALL_DIR}/${KOTLIN_LSP_VERSION}"
readonly BIN_PATH="${HOME}/.local/bin/kotlin-lsp"
readonly MAX_DOWNLOAD_ATTEMPTS=2

resolve_platform() {
  case "$(uname -s)-$(uname -m)" in
    Linux-x86_64)
      ARCHIVE_NAME="kotlin-server-${KOTLIN_LSP_VERSION}.tar.gz"
      ARCHIVE_SHA256="8b4c70e95065420e7867c99aaf9f18e0b4e76311ec453e4c1a39e3f6ae774cbf"
      ;;
    Linux-aarch64)
      ARCHIVE_NAME="kotlin-server-${KOTLIN_LSP_VERSION}-aarch64.tar.gz"
      ARCHIVE_SHA256="c3edd59ef34a7faa4d04f3517afb7a932b19c3f9cf17d1a14e9da17b0b5440ad"
      ;;
    *)
      echo "Unsupported platform for JetBrains kotlin-lsp: $(uname -s)-$(uname -m)" >&2
      return 1
      ;;
  esac
}

verify_archive_sha256() {
  local archive_path="$1"
  local actual
  actual="$(sha256sum "${archive_path}" | awk '{print $1}')"
  if [[ "${actual}" != "${ARCHIVE_SHA256}" ]]; then
    echo "SHA-256 mismatch for ${archive_path}" >&2
    echo "Expected: ${ARCHIVE_SHA256}" >&2
    echo "Actual:   ${actual}" >&2
    return 1
  fi
}

download_archive() {
  mkdir -p "${INSTALL_DIR}"
  local tmp
  tmp="$(mktemp "${INSTALL_DIR}/.${ARCHIVE_NAME}.XXXXXX")"

  if ! curl -fsSL -o "${tmp}" \
    "https://download-cdn.jetbrains.com/language-server/kotlin-server/${KOTLIN_LSP_VERSION}/${ARCHIVE_NAME}"; then
    rm -f "${tmp}"
    echo "curl failed to download JetBrains kotlin-lsp ${KOTLIN_LSP_VERSION}" >&2
    return 1
  fi

  mv -f "${tmp}" "${ARCHIVE_PATH}"
}

extract_archive() {
  rm -rf "${VERSIONED_DIR}"
  mkdir -p "${VERSIONED_DIR}"
  tar -xzf "${ARCHIVE_PATH}" -C "${VERSIONED_DIR}" --strip-components=1
  if [[ ! -x "${VERSIONED_DIR}/bin/intellij-server" ]]; then
    echo "kotlin-lsp launcher not found after extraction" >&2
    return 1
  fi
}

remove_install_artifacts() {
  rm -f "${ARCHIVE_PATH}"
  rm -rf "${VERSIONED_DIR}"
  rm -f "${BIN_PATH}"
}

ensure_server() {
  if [[ -x "${VERSIONED_DIR}/bin/intellij-server" ]]; then
    return 0
  fi

  if [[ -f "${ARCHIVE_PATH}" ]] && ! verify_archive_sha256 "${ARCHIVE_PATH}"; then
    echo "Removing corrupted kotlin-lsp archive for re-download..." >&2
    rm -f "${ARCHIVE_PATH}"
  fi

  local attempt
  for attempt in $(seq 1 "${MAX_DOWNLOAD_ATTEMPTS}"); do
    if [[ ! -f "${ARCHIVE_PATH}" ]]; then
      download_archive || continue
    fi
    if verify_archive_sha256 "${ARCHIVE_PATH}" && extract_archive; then
      return 0
    fi
    echo "Install attempt ${attempt}/${MAX_DOWNLOAD_ATTEMPTS} failed." >&2
    remove_install_artifacts
  done

  echo "Failed to install JetBrains kotlin-lsp after ${MAX_DOWNLOAD_ATTEMPTS} attempts." >&2
  return 1
}

resolve_platform
ARCHIVE_PATH="${INSTALL_DIR}/${ARCHIVE_NAME}"

ensure_server
mkdir -p "${HOME}/.local/bin"
ln -sfn "${VERSIONED_DIR}/bin/intellij-server" "${BIN_PATH}"
