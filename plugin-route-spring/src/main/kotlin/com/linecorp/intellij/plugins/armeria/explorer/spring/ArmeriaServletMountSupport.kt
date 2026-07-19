package com.linecorp.intellij.plugins.armeria.explorer.spring
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.DelegationKind
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaDelegationSupport

/**
 * Spring/servlet-specific mount helpers. Pure target/routeMatch delegation detection
 * lives in [ArmeriaDelegationSupport] so non-Spring collectors can populate
 * `delegationKind` without depending on this module.
 */
object ArmeriaServletMountSupport {
    /**
     * Prefix mounts that should fan Spring MVC controller mappings as delegated children.
     * Exact `.service()` Tomcat mounts stay badge-only.
     */
    fun isExpandableSpringMvcMount(route: ArmeriaRoute): Boolean =
        route.routeMatch == RouteMatch.SERVICE_UNDER &&
            ArmeriaDelegationSupport.detectDelegation(route.target, route.routeMatch) == DelegationKind.SPRING_MVC

    fun detectDelegation(
        target: String,
        routeMatch: RouteMatch,
    ): DelegationKind? = ArmeriaDelegationSupport.detectDelegation(target, routeMatch)

    /**
     * Resolved delegation badge for mounts (from target) and delegated Spring MVC children.
     */
    fun delegationKindOf(route: ArmeriaRoute): DelegationKind? =
        when (route.routeMatch) {
            RouteMatch.DELEGATED_SPRING_MVC -> DelegationKind.SPRING_MVC
            else -> ArmeriaDelegationSupport.detectDelegation(route.target, route.routeMatch)
        }
}
