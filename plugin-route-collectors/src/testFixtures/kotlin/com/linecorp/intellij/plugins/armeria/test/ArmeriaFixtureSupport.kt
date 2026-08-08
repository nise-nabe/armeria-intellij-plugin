package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import java.io.File

fun resolveArmeriaModuleTestDataPath(): String {
    System.getProperty("armeria.moduleTestDataPath")?.takeIf { it.isNotBlank() }?.let { path ->
        val normalized = path.replace('\\', '/')
        require(File(normalized).isDirectory) {
            "armeria.moduleTestDataPath is not a directory: $normalized"
        }
        return normalized
    }
    val fromWorkingDir = File("src/test/testData")
    require(fromWorkingDir.isDirectory) {
        "Expected testData directory at ${fromWorkingDir.absolutePath}; run tests from the owning module root " +
            "or set -Darmeria.moduleTestDataPath"
    }
    return fromWorkingDir.absolutePath.replace('\\', '/')
}

fun allowArmeriaTestSandboxRoots(testRootDisposable: Disposable) {
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

fun clearArmeriaRouteCollectionMetricsForTests() {
    ArmeriaRouteCollectionMetrics.clearLastSnapshotForTests()
}

fun JavaCodeInsightTestFixture.configureArmeriaFixture(relativePath: String): PsiFile {
    val previousTestDataPath = testDataPath
    testDataPath = resolveArmeriaModuleTestDataPath()
    try {
        return configureByFile(relativePath)
    } finally {
        testDataPath = previousTestDataPath
    }
}
