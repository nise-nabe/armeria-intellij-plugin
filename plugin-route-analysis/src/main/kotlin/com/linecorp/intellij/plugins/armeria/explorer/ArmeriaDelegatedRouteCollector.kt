package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaDelegatedRouteCollector {
    fun collect(project: Project, scope: GlobalSearchScope, routes: MutableList<ArmeriaRoute>) {
        val mountRoutes = routes.mapNotNull { route ->
            val delegationKind = ArmeriaServletMountSupport.detectDelegation(route.target, route.routeMatch)
                ?: return@mapNotNull null
            route to delegationKind
        }
        if (mountRoutes.isEmpty()) {
            return
        }

        // Stamp mount badges independently of whether Spring MVC children are discoverable.
        for (i in routes.indices) {
            val route = routes[i]
            val kind = ArmeriaServletMountSupport.detectDelegation(route.target, route.routeMatch) ?: continue
            if (route.delegationKind != kind) {
                routes[i] = route.copy(delegationKind = kind)
            }
        }

        val springCapableMounts = mountRoutes.filter { (_, kind) -> kind == DelegationKind.SPRING_MVC }
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

        for ((mountRoute, _) in springCapableMounts) {
            val scopedSpringMvcRoutes = springMvcRoutesForMount(mountRoute, springMvcRoutes, unassignedModule)
            for (springMvcRoute in scopedSpringMvcRoutes) {
                val combinedPath = ArmeriaRouteSupport.combinePaths(mountRoute.path, springMvcRoute.path)
                val dedupeKey =
                    "${mountRoute.moduleName}:$combinedPath:${springMvcRoute.httpMethod}:${springMvcRoute.target}"
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
                    delegationKind = DelegationKind.SPRING_MVC,
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
