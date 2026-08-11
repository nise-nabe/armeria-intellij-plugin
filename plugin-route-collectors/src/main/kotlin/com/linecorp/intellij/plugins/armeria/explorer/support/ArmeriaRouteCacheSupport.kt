package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.psi.util.PsiModificationTracker

/**
 * Shared [CachedValuesManager] invalidators for route collection and derived memo caches
 * (route collector, blocking inspection paths, gRPC classpath gate).
 */
object ArmeriaRouteCacheSupport {
    fun invalidators(project: Project): Array<Any> =
        arrayOf(
            PsiModificationTracker.MODIFICATION_COUNT,
            ProjectRootModificationTracker.getInstance(project),
            DumbService.getInstance(project).modificationTracker,
            JavaLibraryModificationTracker.getInstance(project),
        )
}
