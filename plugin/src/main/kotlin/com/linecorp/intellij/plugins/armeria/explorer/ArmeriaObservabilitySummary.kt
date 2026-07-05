package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.message

object ArmeriaObservabilitySummary {
    private val OBSERVABILITY_DECORATORS = mapOf(
        "Logging" to "route.explorer.observability.logging",
        "Metrics" to "route.explorer.observability.metrics",
        "Auth" to "route.explorer.observability.auth",
        "Brave" to "route.explorer.observability.tracing",
        "Throttling" to "route.explorer.observability.throttling",
        "Bucket4j" to "route.explorer.observability.rateLimit",
        "Prometheus" to "route.explorer.observability.prometheus",
    )

    fun summarize(routes: List<ArmeriaRoute>): String {
        val decorators = routes.flatMap { it.decorators }.distinct()
        val matched = OBSERVABILITY_DECORATORS.filterKeys { key ->
            decorators.any { decorator -> decorator.contains(key, ignoreCase = true) }
        }
        val parts = buildList {
            matched.values.forEach { key -> add(message(key)) }
            if (routes.any { it.routeMatch == RouteMatch.HEALTH_CHECK }) {
                add(message("route.explorer.observability.healthCheck"))
            }
        }
        if (parts.isEmpty()) {
            return ""
        }
        return message("route.explorer.observability.summary", parts.joinToString(", "))
    }
}
