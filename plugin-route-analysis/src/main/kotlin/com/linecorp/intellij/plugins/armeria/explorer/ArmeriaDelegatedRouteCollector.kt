package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaDelegatedRouteCollector {
    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
    ) {
        // Prefix mounts only: .service() is exact-match and must not invent child paths.
        val springCapableMounts = routes.filter(ArmeriaServletMountSupport::isExpandableSpringMvcMount)
        if (springCapableMounts.isEmpty()) {
            return
        }

        val springMvcRoutes = ArmeriaSpringMvcRouteCollector.collect(project, scope)
        if (springMvcRoutes.isEmpty()) {
            return
        }

        // Same-module multi-mount has no ownership signal; expand under one preferred mount
        // per (module, virtualHost) to avoid Cartesian fan-out across every Spring-capable prefix.
        val preferredMounts = preferredSpringMvcMounts(springCapableMounts)
        val unassignedModule = message("route.explorer.module.unassigned")
        val seenDelegatedKeys = mutableSetOf<String>()
        val delegatedRoutes = mutableListOf<ArmeriaRoute>()

        for (mountRoute in preferredMounts) {
            val scopedSpringMvcRoutes = springMvcRoutesForMount(mountRoute, springMvcRoutes, unassignedModule)
            for (springMvcRoute in scopedSpringMvcRoutes) {
                val combinedPath = ArmeriaRouteSupport.combinePaths(mountRoute.path, springMvcRoute.path)
                // One preferred mount per (module, vhost); dedupe only within that expansion.
                val dedupeKey =
                    "${mountRoute.moduleName}:${mountRoute.virtualHostName}:$combinedPath:" +
                        "${springMvcRoute.httpMethod}:${springMvcRoute.target}"
                if (!seenDelegatedKeys.add(dedupeKey)) {
                    continue
                }
                // Navigate to the mapping-owning method; attribute the tree module to the concrete
                // controller so inherited mappings from other modules still expand under the mount.
                delegatedRoutes +=
                    ArmeriaRoute.create(
                        element = springMvcRoute.element,
                        protocol = RouteProtocol.HTTP.presentableName(),
                        httpMethod = springMvcRoute.httpMethod,
                        path = combinedPath,
                        target = springMvcRoute.target,
                        routeMatch = RouteMatch.DELEGATED_SPRING_MVC,
                        virtualHostName = mountRoute.virtualHostName,
                        delegationMountPath = mountRoute.path,
                        moduleName = springMvcRoute.moduleName(),
                    )
            }
        }

        routes += delegatedRoutes
    }

    /**
     * Picks one expandable Spring MVC mount per `(moduleName, virtualHostName)` group.
     * Prefers the shortest path (broadest prefix); ties break lexicographically.
     */
    internal fun preferredSpringMvcMounts(mounts: List<ArmeriaRoute>): List<ArmeriaRoute> =
        mounts
            .groupBy { it.moduleName to it.virtualHostName }
            .values
            .map { group ->
                group.minWith(
                    compareBy(
                        { ArmeriaRouteSupport.normalizePath(it.path).length },
                        { ArmeriaRouteSupport.normalizePath(it.path) },
                    ),
                )
            }

    internal fun springMvcRoutesForMount(
        mountRoute: ArmeriaRoute,
        springMvcRoutes: List<SpringMvcRoute>,
        unassignedModule: String,
    ): List<SpringMvcRoute> {
        val mountModule = mountRoute.moduleName
        if (mountModule == unassignedModule) {
            return springMvcRoutes
        }
        return springMvcRoutes.filter { route ->
            route.moduleName() == mountModule
        }
    }
}
