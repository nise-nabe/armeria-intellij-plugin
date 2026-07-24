package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import java.io.File

/**
 * [LightJavaCodeInsightFixtureTestCase] with 2026.2+ test sandbox root access for plugin runtime libraries.
 */
abstract class ArmeriaLightJavaCodeInsightFixtureTestCase : LightJavaCodeInsightFixtureTestCase() {
    /**
     * Resolves `src/test/testData` relative to the Gradle test task working directory (module root).
     * Do not override [getTestDataPath] globally — consumer modules without `testData/` rely on the
     * platform default from [LightJavaCodeInsightFixtureTestCase].
     */
    protected fun resolveModuleTestDataPath(): String {
        System.getProperty("armeria.moduleTestDataPath")?.takeIf { it.isNotBlank() }?.let { return it.replace('\\', '/') }
        val fromWorkingDir = File("src/test/testData")
        require(fromWorkingDir.isDirectory) {
            "Expected testData directory at ${fromWorkingDir.absolutePath}; run tests from the owning module root " +
                "or set -Darmeria.moduleTestDataPath"
        }
        return fromWorkingDir.absolutePath.replace('\\', '/')
    }

    override fun setUp() {
        super.setUp()
        allowTestSandboxRoots()
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

        // Fallback for sandboxes that use a different layout.
        VfsRootAccess.allowRootAccess(testRootDisposable, PathManager.getPluginsPath())
        sandboxRoot?.absolutePath?.let { root ->
            VfsRootAccess.allowRootAccess(testRootDisposable, root)
        }
    }
}
