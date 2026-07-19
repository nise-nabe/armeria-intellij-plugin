#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

# Prefetch IntelliJ Ultimate via IPGP so Cursor can checkpoint ~/.gradle (and related
# caches) after install. Subsequent cloud agents then skip the multi-hundred-MB download.
# Soft-fail: feature branches mid-change must not block agent setup.
echo "Warming IntelliJ Platform (Ultimate) for Cursor Cloud snapshot..."
if ! (
  cd "${repo_root}"
  ./gradlew \
    :plugin:compileTestKotlin \
    :plugin-route-model:compileKotlin \
    :plugin-route-collectors:compileTestKotlin \
    :plugin-route-spring:compileTestKotlin \
    :plugin-route-protocol:compileTestKotlin \
    :plugin-route-analysis:compileTestKotlin \
    :plugin-wizard:compileTestKotlin
); then
  echo "Warning: IntelliJ Platform warm failed; the first test run in this session may download the IDE." >&2
fi
