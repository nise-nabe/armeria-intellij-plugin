package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.ModificationTracker
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

    fun modificationTracker(project: Project): ModificationTracker {
        val psi = PsiModificationTracker.getInstance(project)
        val trackers = invalidators(project).mapNotNull { it as? ModificationTracker }
        return ModificationTracker { psi.modificationCount + trackers.sumOf { it.modificationCount } }
    }
}
