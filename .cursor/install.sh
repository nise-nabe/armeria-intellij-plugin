#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "${repo_root}"

ensure_mise() {
  if command -v mise >/dev/null 2>&1; then
    return 0
  fi

  echo "Installing mise..."
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
ensure_mise_activate_in_shell

mise install
mise run cloud:install
