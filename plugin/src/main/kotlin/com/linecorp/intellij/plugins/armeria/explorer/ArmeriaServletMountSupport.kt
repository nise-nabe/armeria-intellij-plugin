package com.linecorp.intellij.plugins.armeria.explorer

internal object ArmeriaServletMountSupport {
    private val SPRING_MVC_SERVICE_MARKERS = setOf(
        "TomcatService",
        "SpringBootService",
    )

    private val SERVLET_SERVICE_MARKERS = setOf(
        "JettyService",
    )

    private val MOUNT_ROUTE_MATCHES = setOf(
        RouteMatch.SERVICE,
        RouteMatch.SERVICE_UNDER,
    )

    fun detectDelegation(target: String, routeMatch: RouteMatch): DelegationKind? {
        if (routeMatch !in MOUNT_ROUTE_MATCHES) {
            return null
        }
        return when {
            SPRING_MVC_SERVICE_MARKERS.any { marker -> target.contains(marker) } -> DelegationKind.SPRING_MVC
            SERVLET_SERVICE_MARKERS.any { marker -> target.contains(marker) } -> DelegationKind.SERVLET
            else -> null
        }
    }

    fun delegatedRouteMatch(kind: DelegationKind): RouteMatch = when (kind) {
        DelegationKind.SPRING_MVC -> RouteMatch.DELEGATED_SPRING_MVC
        DelegationKind.SERVLET -> RouteMatch.DELEGATED_SERVLET
    }
}
