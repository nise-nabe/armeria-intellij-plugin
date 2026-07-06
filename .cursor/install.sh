#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
"${repo_root}/.github/scripts/install-gradle-tapi-mcp.sh"

setup_gh_cli() {
  local gh_source="/exec-daemon/gh"
  if [[ ! -x "${gh_source}" ]]; then
    echo "Warning: ${gh_source} not found; gh CLI will be unavailable in this session." >&2
    return 0
  fi

  if ! mkdir -p "${HOME}/.local/bin" 2>/dev/null; then
    echo "Warning: could not create ${HOME}/.local/bin; gh symlink skipped." >&2
  elif ! ln -sfn "${gh_source}" "${HOME}/.local/bin/gh" 2>/dev/null; then
    echo "Warning: could not symlink gh to ${HOME}/.local/bin/gh." >&2
  fi

  if [[ -w /usr/local/bin ]]; then
    if ! ln -sfn "${gh_source}" /usr/local/bin/gh 2>/dev/null; then
      echo "Warning: could not symlink gh to /usr/local/bin/gh." >&2
    fi
  fi

  if ! "${gh_source}" auth status >/dev/null 2>&1; then
    local token="${GH_TOKEN:-${GITHUB_TOKEN:-}}"
    if [[ -n "${token}" ]]; then
      if ! "${gh_source}" auth login --hostname github.com --git-protocol https --with-token <<<"${token}"; then
        echo "Warning: gh auth login failed; gh CLI may be unavailable for API calls." >&2
        echo "Use ManagePullRequest for PRs, or set GH_TOKEN in Cursor Cloud Secrets." >&2
      fi
    else
      echo "Warning: gh is not authenticated and GH_TOKEN/GITHUB_TOKEN is unset." >&2
      echo "Set GH_TOKEN in Cursor Cloud Secrets, or use the ManagePullRequest tool for PRs." >&2
    fi
  fi

  if ! command -v gh >/dev/null 2>&1; then
    echo "Warning: gh is installed but not on PATH; use /exec-daemon/gh or ~/.local/bin/gh." >&2
  fi
}

setup_gh_cli
