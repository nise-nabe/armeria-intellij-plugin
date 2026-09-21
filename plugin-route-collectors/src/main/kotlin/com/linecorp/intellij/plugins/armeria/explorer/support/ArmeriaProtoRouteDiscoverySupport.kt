package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
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
    private val GRPC_ON_CLASSPATH_KEY = Key.create<CachedValue<Boolean>>("armeria.grpc.on.classpath")

    fun isEnabled(): Boolean =
        try {
            Registry.`is`("armeria.grpc.proto.routes.enabled")
        } catch (_: MissingResourceException) {
            true
        }

    /**
     * Whether [GRPC_SERVICE_CLASS] is on the project classpath (gRPC proto overlay prerequisite).
     *
     * `GrpcService` lives in a library jar, so it is resolved with [GlobalSearchScope.allScope].
     * The lookup is cached per project and invalidated with library/PSI/root changes so gutter
     * markers do not repeat [JavaPsiFacade.findClass] on every `rpc` token visit.
     */
    fun isGrpcOnClasspath(project: Project): Boolean =
        CachedValuesManager.getManager(project).getCachedValue(
            project,
            GRPC_ON_CLASSPATH_KEY,
            CachedValueProvider {
                val onClasspath =
                    JavaPsiFacade
                        .getInstance(project)
                        .findClass(GRPC_SERVICE_CLASS, GlobalSearchScope.allScope(project)) != null
                CachedValueProvider.Result.create(
                    onClasspath,
                    *ArmeriaRouteCacheSupport.invalidators(project),
                )
            },
            false,
        )
}
