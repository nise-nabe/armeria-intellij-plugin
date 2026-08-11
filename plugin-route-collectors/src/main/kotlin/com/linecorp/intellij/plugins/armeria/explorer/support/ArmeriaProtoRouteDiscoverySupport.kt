package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.java.library.JavaLibraryModificationTracker
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import java.util.MissingResourceException

/**
 * Shared gate for gRPC proto route discovery (`armeria.grpc.proto.routes.enabled` in plugin.xml).
 *
 * Defaults to enabled when the registry key is absent (e.g. in lightweight test environments).
 * The registry value is checked outside route caches so toggling the kill-switch takes effect
 * immediately without waiting for PSI or project-root invalidation.
 *
 * Route Explorer proto collection and proto RPC gutter markers both require [isEnabled] and
 * [isGrpcOnClasspath].
 */
object ArmeriaProtoRouteDiscoverySupport {
    private const val GRPC_SERVICE_CLASS = "com.linecorp.armeria.server.grpc.GrpcService"

    fun isEnabled(): Boolean =
        try {
            Registry.`is`("armeria.grpc.proto.routes.enabled")
        } catch (_: MissingResourceException) {
            true
        }

    /**
     * Whether [GRPC_SERVICE_CLASS] is resolvable in [scope] (gRPC proto overlay prerequisite).
     *
     * Project-scoped lookups are cached and invalidated with library/PSI/root changes so gutter
     * markers do not repeat [JavaPsiFacade.findClass] on every `rpc` token visit.
     */
    fun isGrpcOnClasspath(
        project: Project,
        scope: GlobalSearchScope,
    ): Boolean {
        if (scope != GlobalSearchScope.projectScope(project)) {
            return JavaPsiFacade.getInstance(project).findClass(GRPC_SERVICE_CLASS, scope) != null
        }
        return CachedValuesManager.getManager(project).getCachedValue(project) {
            val onClasspath = JavaPsiFacade.getInstance(project).findClass(GRPC_SERVICE_CLASS, scope) != null
            CachedValueProvider.Result.create(
                onClasspath,
                PsiModificationTracker.MODIFICATION_COUNT,
                ProjectRootModificationTracker.getInstance(project),
                DumbService.getInstance(project).modificationTracker,
                JavaLibraryModificationTracker.getInstance(project),
            )
        }
    }
}
