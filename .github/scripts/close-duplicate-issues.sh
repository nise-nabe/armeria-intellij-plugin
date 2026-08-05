#!/usr/bin/env bash
# Close duplicate and already-resolved GitHub issues filed as Thermo P3 follow-ups.
# Requires a token with issues:write (e.g. gh auth login or GH_TOKEN with repo scope).
set -euo pipefail

REPO="${GITHUB_REPOSITORY:-nise-nabe/armeria-intellij-plugin}"

if ! command -v gh >/dev/null 2>&1; then
  echo "gh CLI is required" >&2
  exit 1
fi

if ! gh auth status >/dev/null 2>&1; then
  echo "gh auth required (issues:write on ${REPO})" >&2
  exit 1
fi

close_dup() {
  local dup="$1" canonical="$2" reason="$3"
  echo "Closing #${dup} (duplicate of #${canonical})"
  gh issue close "$dup" --repo "$REPO" --comment "Duplicate of #${canonical}. ${reason}"
}

close_done() {
  local num="$1" message="$2"
  echo "Closing #${num} (resolved)"
  gh issue close "$num" --repo "$REPO" --comment "$message"
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
echo "Done. #362 remains open (optional perf memoization). #337 remains open (incremental testData/ migration)."
