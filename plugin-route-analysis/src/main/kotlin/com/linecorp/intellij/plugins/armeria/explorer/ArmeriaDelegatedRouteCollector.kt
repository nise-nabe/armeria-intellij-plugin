package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaDelegatedRouteCollector {
    fun collect(project: Project, scope: GlobalSearchScope, routes: MutableList<ArmeriaRoute>) {
        // Prefix mounts only: .service() is exact-match and must not invent child paths.
        val springCapableMounts = routes.filter { route ->
            route.routeMatch == RouteMatch.SERVICE_UNDER &&
                ArmeriaServletMountSupport.detectDelegation(route.target, route.routeMatch) ==
                DelegationKind.SPRING_MVC
        }
        if (springCapableMounts.isEmpty()) {
            return
        }

        val springMvcRoutes = ArmeriaSpringMvcRouteCollector.collect(project, scope)
        if (springMvcRoutes.isEmpty()) {
            return
        }

        val unassignedModule = message("route.explorer.module.unassigned")
        val seenDelegatedKeys = mutableSetOf<String>()
        val delegatedRoutes = mutableListOf<ArmeriaRoute>()

        for (mountRoute in springCapableMounts) {
            val scopedSpringMvcRoutes = springMvcRoutesForMount(mountRoute, springMvcRoutes, unassignedModule)
            for (springMvcRoute in scopedSpringMvcRoutes) {
                val combinedPath = ArmeriaRouteSupport.combinePaths(mountRoute.path, springMvcRoute.path)
                // Include mount path so distinct mounts that combine to the same path keep separate children.
                val dedupeKey =
                    "${mountRoute.moduleName}:${mountRoute.virtualHostName}:${mountRoute.path}:$combinedPath:" +
                        "${springMvcRoute.httpMethod}:${springMvcRoute.target}"
                if (!seenDelegatedKeys.add(dedupeKey)) {
                    continue
                }
                delegatedRoutes += ArmeriaRoute.create(
                    element = springMvcRoute.element,
                    protocol = RouteProtocol.HTTP.presentableName(),
                    httpMethod = springMvcRoute.httpMethod,
                    path = combinedPath,
                    target = springMvcRoute.target,
                    routeMatch = RouteMatch.DELEGATED_SPRING_MVC,
                    virtualHostName = mountRoute.virtualHostName,
                    delegationMountPath = mountRoute.path,
                )
            }
        }

        routes += delegatedRoutes
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
            ArmeriaRouteMetadata.moduleName(route.element) == mountModule
        }
    }
}
