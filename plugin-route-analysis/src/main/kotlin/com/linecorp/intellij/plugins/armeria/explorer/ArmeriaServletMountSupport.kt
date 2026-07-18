package com.linecorp.intellij.plugins.armeria.explorer

internal object ArmeriaServletMountSupport {
    private val SPRING_MVC_SERVICE_NAMES = setOf(
        "TomcatService",
    )

    private val SERVLET_SERVICE_NAMES = setOf(
        "JettyService",
    )

    private val MOUNT_ROUTE_MATCHES = setOf(
        RouteMatch.SERVICE,
        RouteMatch.SERVICE_UNDER,
    )

    /**
     * Prefix mounts that should fan Spring MVC controller mappings as delegated children.
     * Exact `.service()` Tomcat mounts stay badge-only.
     */
    fun isExpandableSpringMvcMount(route: ArmeriaRoute): Boolean =
        route.routeMatch == RouteMatch.SERVICE_UNDER &&
            detectDelegation(route.target, route.routeMatch) == DelegationKind.SPRING_MVC

    fun detectDelegation(target: String, routeMatch: RouteMatch): DelegationKind? {
        if (routeMatch !in MOUNT_ROUTE_MATCHES) {
            return null
        }
        val simpleName = targetSimpleName(target)
        return when (simpleName) {
            in SPRING_MVC_SERVICE_NAMES -> DelegationKind.SPRING_MVC
            in SERVLET_SERVICE_NAMES -> DelegationKind.SERVLET
            else -> null
        }
    }

    /**
     * Resolved delegation badge for mounts (from target) and delegated Spring MVC children.
     */
    fun delegationKindOf(route: ArmeriaRoute): DelegationKind? =
        when (route.routeMatch) {
            RouteMatch.DELEGATED_SPRING_MVC -> DelegationKind.SPRING_MVC
            else -> detectDelegation(route.target, route.routeMatch)
        }

    private fun targetSimpleName(target: String): String =
        target.substringBefore('(').substringAfterLast('.').trim().trimEnd('?')
}
