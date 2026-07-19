package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import java.io.File

/**
 * [LightJavaCodeInsightFixtureTestCase] with 2026.2+ test sandbox root access for plugin runtime libraries.
 */
abstract class ArmeriaLightJavaCodeInsightFixtureTestCase : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        allowTestSandboxRoots()
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
