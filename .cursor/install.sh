#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

ensure_mise() {
  if command -v mise >/dev/null 2>&1; then
    return 0
  fi

  echo "Installing mise..."
  # mise.run bundles SHA-256 checksums for the pinned release it installs.
  curl -fsSL https://mise.run | sh
  export PATH="${HOME}/.local/bin:${PATH}"
}

ensure_mise_activate_in_shell() {
  local marker='# cursor-cloud: mise activate'
  if [[ -f "${HOME}/.bashrc" ]] && grep -qF "${marker}" "${HOME}/.bashrc" 2>/dev/null; then
    return 0
  fi

  cat >>"${HOME}/.bashrc" <<'EOF'
# cursor-cloud: mise activate
eval "$(mise activate bash)"
EOF
}

ensure_mise
export PATH="${HOME}/.local/bin:${PATH}"
eval "$(mise activate bash)"

# MCP JAR has no Java dependency; install before mise-managed Java so a transient
# Java download failure does not block Gradle MCP setup.
bash "${repo_root}/.github/scripts/install-gradle-tapi-mcp.sh"

mise run cloud:install
ensure_mise_activate_in_shell
