package com.linecorp.intellij.plugins.armeria.explorer.spring
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.DelegationKind
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch

/**
 * Spring-specific mount expansion policy. Pure target/routeMatch delegation detection
 * lives in [com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaDelegationSupport]
 * so collectors can set [ArmeriaRoute.delegationKind] without depending on this module.
 *
 * Stored [ArmeriaRoute.delegationKind] is the source of truth for badges and expansion;
 * collectors set it at emit time.
 */
object ArmeriaServletMountSupport {
    /**
     * Prefix mounts that should fan Spring MVC controller mappings as delegated children.
     * Exact `.service()` Tomcat mounts stay badge-only.
     */
    fun isExpandableSpringMvcMount(route: ArmeriaRoute): Boolean =
        route.routeMatch == RouteMatch.SERVICE_UNDER &&
            route.delegationKind == DelegationKind.SPRING_MVC
}
