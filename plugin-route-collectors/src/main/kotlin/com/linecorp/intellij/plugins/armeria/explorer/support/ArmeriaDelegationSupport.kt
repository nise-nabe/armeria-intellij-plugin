package com.linecorp.intellij.plugins.armeria.explorer.support
import com.linecorp.intellij.plugins.armeria.explorer.model.DelegationKind
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch

/**
 * Pure delegation detection for `ServerBuilder.service`/`.serviceUnder` targets.
 *
 * Lives in `support` (no Spring/servlet PSI dependencies) so that
 * [plugin-route-collectors] can set `delegationKind` on service registrations
 * without depending on [plugin-route-spring]. Spring-specific expansion predicates
 * such as `isExpandableSpringMvcMount` remain in
 * `explorer.spring.ArmeriaServletMountSupport` and read the stored kind.
 */
object ArmeriaDelegationSupport {
    private val SPRING_MVC_SERVICE_NAMES =
        setOf(
            "TomcatService",
        )

    private val SERVLET_SERVICE_NAMES =
        setOf(
            "JettyService",
        )

    private val MOUNT_ROUTE_MATCHES =
        setOf(
            RouteMatch.SERVICE,
            RouteMatch.SERVICE_UNDER,
        )

    fun detectDelegation(
        target: String,
        routeMatch: RouteMatch,
    ): DelegationKind? {
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
        target
            .substringBefore('(')
            .substringAfterLast('.')
            .trim()
            .trimEnd('?')
}
