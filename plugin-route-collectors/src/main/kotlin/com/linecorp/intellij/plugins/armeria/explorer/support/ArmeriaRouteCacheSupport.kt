package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.lang.Language
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
    private val ROUTE_PSI_LANGUAGE_IDS =
        setOf(
            "java",
            "kotlin",
            "scala",
            "yaml",
            "properties",
            "protobuf",
            "graphql",
            "thrift",
            // YAML/IDL config can be opened as plain text when optional language plugins are absent.
            "text",
        )

    fun invalidators(project: Project): Array<Any> =
        arrayOf(
            PsiModificationTracker.getInstance(project).forLanguages(::isRoutePsiLanguage),
            ProjectRootModificationTracker.getInstance(project),
            DumbService.getInstance(project).modificationTracker,
            JavaLibraryModificationTracker.getInstance(project),
        )

    fun modificationTracker(project: Project): ModificationTracker {
        val trackers = invalidators(project).mapNotNull { it as? ModificationTracker }
        return ModificationTracker { trackers.sumOf { it.modificationCount } }
    }

    internal fun isRoutePsiLanguage(language: Language): Boolean =
        generateSequence(language) { it.baseLanguage }
            .any { isRoutePsiLanguageId(it.id) }

    internal fun isRoutePsiLanguageId(id: String): Boolean = id.lowercase() in ROUTE_PSI_LANGUAGE_IDS
}
