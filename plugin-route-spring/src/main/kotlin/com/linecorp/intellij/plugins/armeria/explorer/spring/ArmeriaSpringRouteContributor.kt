package com.linecorp.intellij.plugins.armeria.explorer.spring

import com.intellij.psi.JavaPsiFacade
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinPluginSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteCollectContext
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteContributor

/**
 * Collects Spring Boot and delegated Spring MVC routes. Wired in production via
 * `ArmeriaRouteAnalysisCollector` in `plugin-route-analysis`.
 */
object ArmeriaSpringRouteContributor : RouteContributor {
    override fun collect(context: RouteCollectContext) {
        val psiFacade = JavaPsiFacade.getInstance(context.project)
        val springBootArmeriaAvailable = ArmeriaRouteSupport.isSpringBootArmeriaAvailable(psiFacade, context.scope)
        if (ArmeriaKotlinPluginSupport.isKotlinPluginAvailable() && springBootArmeriaAvailable) {
            ArmeriaKotlinSpringBootRouteCollector.collect(context)
        }
        if (springBootArmeriaAvailable) {
            ArmeriaSpringBootRouteCollector.collect(context)
            ArmeriaSpringConfigRouteCollector.collect(
                context.project,
                context.scope,
                context.routes,
                context.seenConfigRoutes,
            )
        }
        ArmeriaDelegatedRouteCollector.collect(context.project, context.scope, context.routes)
    }
}
