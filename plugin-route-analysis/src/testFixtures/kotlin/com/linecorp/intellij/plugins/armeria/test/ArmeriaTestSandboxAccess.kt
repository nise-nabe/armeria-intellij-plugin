package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import java.io.File

/**
 * Allows IntelliJ Platform test sandboxes to read plugin runtime libraries under
 * `plugins-test/.../lib` on 2026.2+, where [VfsRootAccess] no longer whitelists that tree by default.
 */
internal object ArmeriaTestSandboxAccess {
    private val allowedRoots = mutableSetOf<String>()

    fun allowTestSandboxRoots() {
        allow(PathManager.getPluginsPath())
        File(PathManager.getConfigPath()).parentFile?.absolutePath?.let(::allow)
    }

    fun disallowTestSandboxRoots() {
        val roots = allowedRoots.toList()
        allowedRoots.clear()
        roots.forEach(VfsRootAccess::disallowRootAccess)
    }

    private fun allow(path: String) {
        if (allowedRoots.add(path)) {
            VfsRootAccess.allowRootAccess(path)
        }
    }
}
