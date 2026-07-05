package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope

internal object ArmeriaDelegatedRouteCollector {
    fun collect(project: Project, scope: GlobalSearchScope, routes: MutableList<ArmeriaRoute>) {
        val springMvcRoutes = ArmeriaSpringMvcRouteCollector.collect(project, scope)
        if (springMvcRoutes.isEmpty()) {
            return
        }

        val mountRoutes = routes.mapNotNull { route ->
            val delegationKind = ArmeriaServletMountSupport.detectDelegation(route.target, route.routeMatch) ?: return@mapNotNull null
            route to delegationKind
        }
        if (mountRoutes.isEmpty()) {
            return
        }

        val seenDelegatedKeys = mutableSetOf<String>()
        val delegatedRoutes = mutableListOf<ArmeriaRoute>()
        val mountPathsWithDelegation = mutableSetOf<String>()

        for ((mountRoute, delegationKind) in mountRoutes) {
            mountPathsWithDelegation += mountRoute.path
            for (springMvcRoute in springMvcRoutes) {
                val combinedPath = ArmeriaRouteSupport.combinePaths(mountRoute.path, springMvcRoute.path)
                val dedupeKey = "${mountRoute.moduleName}:$combinedPath:${springMvcRoute.httpMethod}:${springMvcRoute.target}"
                if (!seenDelegatedKeys.add(dedupeKey)) {
                    continue
                }
                delegatedRoutes += ArmeriaRoute.create(
                    element = springMvcRoute.element,
                    protocol = RouteProtocol.HTTP.presentableName(),
                    httpMethod = springMvcRoute.httpMethod,
                    path = combinedPath,
                    target = springMvcRoute.target,
                    routeMatch = ArmeriaServletMountSupport.delegatedRouteMatch(delegationKind),
                    delegationKind = delegationKind,
                    delegationMountPath = mountRoute.path,
                )
            }
        }

        val updatedRoutes = routes.map { route ->
            val delegationKind = ArmeriaServletMountSupport.detectDelegation(route.target, route.routeMatch)
            if (delegationKind != null && route.path in mountPathsWithDelegation) {
                route.copy(delegationKind = delegationKind)
            } else {
                route
            }
        }
        routes.clear()
        routes += updatedRoutes
        routes += delegatedRoutes
    }
}
