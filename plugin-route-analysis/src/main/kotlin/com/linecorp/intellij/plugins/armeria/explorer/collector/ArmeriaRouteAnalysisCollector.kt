package com.linecorp.intellij.plugins.armeria.explorer.collector

import com.intellij.openapi.project.Project
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.protocol.ArmeriaProtocolRouteContributor
import com.linecorp.intellij.plugins.armeria.explorer.spring.ArmeriaSpringRouteContributor
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteContributor

/**
 * Production entry point for full route collection. Always supplies Spring and protocol
 * contributors so callers cannot silently omit them.
 */
object ArmeriaRouteAnalysisCollector {
    private val CONTRIBUTORS: List<RouteContributor> =
        listOf(ArmeriaSpringRouteContributor, ArmeriaProtocolRouteContributor)

    fun collect(
        project: Project,
        includeProtoRoutes: Boolean = false,
    ): List<ArmeriaRoute> =
        ArmeriaRouteCollector.collect(
            project = project,
            includeProtoRoutes = includeProtoRoutes,
            contributors = CONTRIBUTORS,
        )
}
