#!/usr/bin/env bash
# Remove stale IntelliJ Platform test sandbox indexes that cause
# PersistentEnumerator / index corruption failures in :plugin:test.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SANDBOX="${ROOT}/.intellijPlatform/sandbox/plugin"

if [[ ! -d "${SANDBOX}" ]]; then
  echo "No test sandbox at ${SANDBOX}; nothing to clean."
  exit 0
fi

removed=0
while IFS= read -r -d '' dir; do
  rm -rf "${dir}"
  echo "Removed ${dir}"
  removed=$((removed + 1))
done < <(find "${SANDBOX}" -mindepth 1 -maxdepth 2 -type d -name system-test -print0 2>/dev/null || true)

if [[ "${removed}" -eq 0 ]]; then
  echo "No system-test directories found under ${SANDBOX}."
else
  echo "Cleaned ${removed} sandbox director(y/ies). Re-run :plugin:test or build."
fi
