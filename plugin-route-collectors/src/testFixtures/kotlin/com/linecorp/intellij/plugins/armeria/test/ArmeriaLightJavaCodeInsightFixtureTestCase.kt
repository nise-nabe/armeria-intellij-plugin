package com.linecorp.intellij.plugins.armeria.test

import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * [LightJavaCodeInsightFixtureTestCase] with 2026.2+ test sandbox root access for plugin runtime libraries.
 */
abstract class ArmeriaLightJavaCodeInsightFixtureTestCase : LightJavaCodeInsightFixtureTestCase() {
    /**
     * Resolves `src/test/testData` relative to the Gradle test task working directory (module root).
     * Do not override [getTestDataPath] globally — consumer modules without `testData/` rely on the
     * platform default from [LightJavaCodeInsightFixtureTestCase].
     */
    protected fun resolveModuleTestDataPath(): String = resolveArmeriaModuleTestDataPath()

    /**
     * Loads a fixture from [resolveModuleTestDataPath] and resets [myFixture.testDataPath] afterward so
     * later [configureByText] calls do not accidentally resolve relative paths against testData.
     */
    protected fun configureFixture(relativePath: String): PsiFile =
        myFixture.configureArmeriaFixture(relativePath) { resolveModuleTestDataPath() }

    override fun setUp() {
        suppressUltimatePostStartupConstructorErrors()
        super.setUp()
        allowArmeriaTestSandboxRoots(testRootDisposable)
    }

    override fun tearDown() {
        try {
            clearArmeriaRouteCollectionMetricsForTests()
        } finally {
            super.tearDown()
        }
    }

    companion object {
        init {
            suppressUltimatePostStartupConstructorErrors()
        }
    }
}
