package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_LATEST_WITH_LATEST_JDK
import com.intellij.testFramework.rules.TestNameExtension
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import org.junit.jupiter.api.extension.AfterEachCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.File

/**
 * JUnit 5 wrapper around [LightJavaCodeInsightFixtureTestCase] with Armeria sandbox and testData helpers.
 */
abstract class ArmeriaLightJavaCodeInsightFixtureTestCase5(
    private val lightProjectDescriptor: LightProjectDescriptor? = null,
) {
    @RegisterExtension
    protected val testNameRule: TestNameExtension = TestNameExtension()

    @JvmField
    @RegisterExtension
    protected val testCase: ArmeriaLightFixtureCase = ArmeriaLightFixtureCase(lightProjectDescriptor, this)

    protected open fun onFixtureSetUp() {}

    protected val myFixture get() = testCase.fixture

    protected val project get() = testCase.projectForTests

    protected val module get() = testCase.moduleForTests

    protected fun resolveModuleTestDataPath(): String = resolveArmeriaModuleTestDataPath()

    protected fun configureFixture(relativePath: String): PsiFile {
        val previousTestDataPath = myFixture.testDataPath
        myFixture.testDataPath = resolveModuleTestDataPath()
        try {
            return myFixture.configureByFile(relativePath)
        } finally {
            myFixture.testDataPath = previousTestDataPath
        }
    }

    class ArmeriaLightFixtureCase(
        private val lightProjectDescriptor: LightProjectDescriptor?,
        private val owner: ArmeriaLightJavaCodeInsightFixtureTestCase5,
    ) : LightJavaCodeInsightFixtureTestCase(),
        BeforeEachCallback,
        AfterEachCallback {
        override fun getProjectDescriptor(): LightProjectDescriptor = lightProjectDescriptor ?: JAVA_LATEST_WITH_LATEST_JDK

        override fun beforeEach(context: ExtensionContext) {
            setUp()
        }

        override fun afterEach(context: ExtensionContext) {
            tearDown()
        }

        val fixture get() = myFixture

        val projectForTests get() = project

        val moduleForTests get() = module

        override fun setUp() {
            super.setUp()
            allowTestSandboxRoots()
            owner.onFixtureSetUp()
        }

        override fun tearDown() {
            try {
                ArmeriaRouteCollectionMetrics.clearLastSnapshotForTests()
            } finally {
                super.tearDown()
            }
        }

        private fun allowTestSandboxRoots() {
            val sandboxRoot = File(PathManager.getConfigPath()).parentFile
            val pluginsTestDir = sandboxRoot?.resolve("plugins-test")
            if (pluginsTestDir != null && pluginsTestDir.isDirectory) {
                VfsRootAccess.allowRootAccess(testRootDisposable, pluginsTestDir.absolutePath)
                return
            }

            VfsRootAccess.allowRootAccess(testRootDisposable, PathManager.getPluginsPath())
            sandboxRoot?.absolutePath?.let { root ->
                VfsRootAccess.allowRootAccess(testRootDisposable, root)
            }
        }
    }
}
