#!/usr/bin/env bash
# One-shot cleanup: close duplicate and already-resolved Thermo P3 follow-up issues.
# Requires a token with issues:write (e.g. gh auth login or GH_TOKEN with repo scope).
set -euo pipefail

REPO="${GITHUB_REPOSITORY:-nise-nabe/armeria-intellij-plugin}"
FAILURES=0

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required" >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "gh auth required (issues:write on ${REPO})" >&2
  exit 1
fi

issue_state() {
  local num="$1"
  gh issue view "$num" --repo "$REPO" --json state --jq '.state' 2>/dev/null || echo "MISSING"
}

close_dup() {
  local dup="$1" canonical="$2" reason="$3"
  local state
  state="$(issue_state "$dup")"
  if [[ "$state" != "OPEN" ]]; then
    echo "Skipping #${dup} (state=${state})"
    return 0
  fi
  echo "Closing #${dup} as duplicate of #${canonical}"
  if gh issue close "$dup" \
    --repo "$REPO" \
    --duplicate-of "$canonical" \
    --reason duplicate \
    --comment "Duplicate of #${canonical}. ${reason}"; then
    return 0
  fi
  echo "Failed to close #${dup}" >&2
  FAILURES=$((FAILURES + 1))
}

close_done() {
  local num="$1" message="$2"
  local state
  state="$(issue_state "$num")"
  if [[ "$state" != "OPEN" ]]; then
    echo "Skipping #${num} (state=${state})"
    return 0
  fi
  echo "Closing #${num} (resolved)"
  if gh issue close "$num" \
    --repo "$REPO" \
    --reason completed \
    --comment "$message"; then
    return 0
  fi
  echo "Failed to close #${num}" >&2
  FAILURES=$((FAILURES + 1))
}

echo "=== Duplicates (close in favor of canonical issue) ==="

close_dup 341 350 \
  "Same \`armeria.moduleTestDataPath\` Gradle convention extraction from PR #338."

close_dup 347 350 \
  "Same build-logic convention for \`armeria.moduleTestDataPath\`."

close_dup 349 350 \
  "Same \`configureArmeriaModuleTestData()\`-style convention request."

close_dup 342 346 \
  "Both tracked exposing \`configureFixture()\` on \`ArmeriaLightJavaCodeInsightFixtureTestCase\`."

close_dup 348 343 \
  "Same \`fastTest\` coverage request for \`ArmeriaRouteTestSupport\` helpers."

close_dup 334 328 \
  "Both requested a \`ProjectRootModificationTracker\` proto-cache regression test."

close_dup 335 328 \
  "Warm proto cache + gRPC classpath gate regression is covered by the same scenario as #328."

close_dup 361 362 \
  "Same optional blocking-client path-filter memoization follow-up from PR #356."

echo ""
echo "=== Resolved on main (close canonical / standalone follow-ups) ==="

close_done 350 \
  "Resolved on \`main\`: \`com.linecorp.intellij.platform-library\` sets \`armeria.moduleTestDataPath\` when \`src/test/testData/\` exists (\`build-logic/src/main/kotlin/com.linecorp.intellij.platform-library.gradle.kts\`). Skill documents the convention."

close_done 346 \
  "Resolved on \`main\`: \`configureFixture()\` lives on \`ArmeriaLightJavaCodeInsightFixtureTestCase\` and resets \`testDataPath\` in a \`finally\` block (\`plugin-route-collectors/src/testFixtures/.../ArmeriaLightJavaCodeInsightFixtureTestCase.kt\`)."

close_done 343 \
  "Resolved on \`main\`: \`ArmeriaRouteTestSupportTest\` in \`plugin-route-collectors/src/fastTest\` covers \`route()\`, \`singleRoute()\`, and \`assertRoute()\` edge cases."

close_done 328 \
  "Resolved on \`main\`: \`ArmeriaGrpcRouteCollectorGateTest\` covers classpath gate warm-cache invalidation (\`testProtoRoutesAppearWhenGrpcClasspathBecomesAvailable\`) and project-root invalidation (\`testWarmProtoOverlayCacheInvalidatesOnProjectRootChange\`)."

close_done 339 \
  "Resolved on \`main\`: \`intellij-armeria-plugin\` skill cross-references the \`testData/\` convention in \`armeria-route-psi-analysis\`."

close_done 340 \
  "Resolved on \`main\`: \`assertRoute\` KDoc documents ambiguous path lookups; \`ArmeriaRouteTestSupportTest.testAssertRouteFailsWhenPathIsAmbiguous\` guards the behavior."

close_done 344 \
  "Resolved on \`main\`: \`ArmeriaExtendedRegistrationCollectorBasicTest\` uses \`collectRoutes().also { it.singleRoute() }.assertRoute(...)\` for all registration cases."

close_done 345 \
  "Resolved on \`main\`: \`resolveArmeriaModuleTestDataPath()\` validates the system property; \`ArmeriaModuleTestDataPathTest\` covers invalid/valid/fallback paths."

close_done 351 \
  "Resolved on \`main\`: \`configureFixture()\` restores the previous \`testDataPath\` in \`finally\` so inline \`configureByText\` is not affected."

close_done 352 \
  "Resolved on \`main\`: \`.run/Armeria testData fixture.run.xml\` plus skill documentation for IDE ad-hoc runs."

echo ""
if [[ "$FAILURES" -gt 0 ]]; then
  echo "Done with ${FAILURES} failure(s). #362 remains open (optional perf). #337 remains open (incremental testData/ migration)." >&2
  exit 1
fi

echo "Done. #362 remains open (optional perf memoization). #337 remains open (incremental testData/ migration)."
