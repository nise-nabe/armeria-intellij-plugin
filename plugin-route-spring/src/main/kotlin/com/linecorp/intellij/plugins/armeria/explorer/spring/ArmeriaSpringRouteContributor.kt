package com.linecorp.intellij.plugins.armeria.explorer.spring

import com.intellij.psi.JavaPsiFacade
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinPluginSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteCollectContext
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteContributor

/**
 * Collects Spring Boot and delegated Spring MVC routes. Registered in production via
 * `ArmeriaRouteContributorBootstrap` in `plugin-route-analysis`.
 *
 * Skips when [RouteCollectContext.includeProtoRoutes] is true (proto-only overlay pass).
 */
object ArmeriaSpringRouteContributor : RouteContributor {
    override fun collect(context: RouteCollectContext) {
        if (context.includeProtoRoutes) return
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
