package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import java.util.MissingResourceException

/**
 * Shared gate for gRPC proto route discovery (`armeria.grpc.proto.routes.enabled` in plugin.xml).
 *
 * Defaults to enabled when the registry key is absent (e.g. in lightweight test environments).
 * The registry value is checked outside route caches so toggling the kill-switch takes effect
 * immediately without waiting for PSI or project-root invalidation.
 */
object ArmeriaProtoRouteDiscoverySupport {
    private const val GRPC_SERVICE_CLASS = "com.linecorp.armeria.server.grpc.GrpcService"

    fun isEnabled(): Boolean =
        try {
            Registry.`is`("armeria.grpc.proto.routes.enabled")
        } catch (_: MissingResourceException) {
            true
        }

    /** Whether [GRPC_SERVICE_CLASS] is resolvable in [scope] (gRPC proto overlay prerequisite). */
    fun isGrpcOnClasspath(
        project: Project,
        scope: GlobalSearchScope,
    ): Boolean = JavaPsiFacade.getInstance(project).findClass(GRPC_SERVICE_CLASS, scope) != null
}
