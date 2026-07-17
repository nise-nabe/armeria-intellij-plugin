package com.linecorp.intellij.plugins.armeria.explorer

internal object ArmeriaServletMountSupport {
    private val SPRING_MVC_SERVICE_NAMES = setOf(
        "TomcatService",
        "SpringBootService",
    )

    private val SERVLET_SERVICE_NAMES = setOf(
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
        val simpleName = targetSimpleName(target)
        return when (simpleName) {
            in SPRING_MVC_SERVICE_NAMES -> DelegationKind.SPRING_MVC
            in SERVLET_SERVICE_NAMES -> DelegationKind.SERVLET
            else -> null
        }
    }

    private fun targetSimpleName(target: String): String =
        target.substringBefore('(').substringAfterLast('.').trim()
}
