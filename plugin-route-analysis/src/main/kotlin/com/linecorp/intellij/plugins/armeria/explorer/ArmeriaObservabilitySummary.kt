package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.message

object ArmeriaObservabilitySummary {
    fun summarize(routes: List<ArmeriaRoute>): String {
        val relevantRoutes = routes.filterNot { it.isDocService }
        val parts = buildList {
            addAll(ArmeriaDecoratorSupport.observabilitySignals(relevantRoutes.flatMap { it.decorators }.distinct()))
            if (relevantRoutes.any { it.routeMatch == RouteMatch.HEALTH_CHECK }) {
                add(message("route.explorer.observability.healthCheck"))
            }
        }
        if (parts.isEmpty()) {
            return ""
        }
        return message("route.explorer.observability.summary", parts.joinToString(", "))
    }
}
