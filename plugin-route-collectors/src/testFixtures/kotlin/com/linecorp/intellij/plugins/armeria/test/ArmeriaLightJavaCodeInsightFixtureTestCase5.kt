package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase5
import com.intellij.testFramework.junit5.RunInEdt
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach

/**
 * Platform [LightJavaCodeInsightFixtureTestCase5] with Armeria sandbox roots, testData helpers, and metrics teardown.
 */
@RunInEdt(writeIntent = true)
abstract class ArmeriaLightJavaCodeInsightFixtureTestCase5(
    projectDescriptor: LightProjectDescriptor? = null,
) : LightJavaCodeInsightFixtureTestCase5(projectDescriptor) {
    private var vfsRootDisposable: Disposable? = null

    protected val myFixture: JavaCodeInsightTestFixture get() = fixture

    protected val project get() = fixture.project

    protected val module get() = fixture.module

    protected open fun onFixtureSetUp() {}

    protected fun resolveModuleTestDataPath(): String = resolveArmeriaModuleTestDataPath()

    protected fun configureFixture(relativePath: String): PsiFile = fixture.configureArmeriaFixture(relativePath)

    /**
     * Avoid [com.intellij.JavaTestUtil.getRelativeJavaTestDataPath] during fixture setUp; Armeria tests
     * load files via [configureFixture] / [resolveModuleTestDataPath] instead.
     */
    override fun getRelativePath(): String = ""

    @BeforeEach
    fun armeriaFixtureSetUp() {
        val disposable = Disposer.newDisposable("ArmeriaFixtureVfsRoot")
        vfsRootDisposable = disposable
        Disposer.register(fixture.project, disposable)
        allowArmeriaTestSandboxRoots(disposable)
        onFixtureSetUp()
    }

    @AfterEach
    fun armeriaFixtureTearDown() {
        clearArmeriaRouteCollectionMetricsForTests()
        vfsRootDisposable = null
    }
}
