package com.linecorp.intellij.plugins.armeria.explorer.ui

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaObservabilitySummary {
    // Subset of known decorator simple names that carry observable signals.
    // Mirrors the observabilityKey entries in ArmeriaDecoratorSupport.KNOWN_DECORATORS.
    private val OBSERVABILITY_KEYS =
        mapOf(
            "LoggingService" to "route.explorer.observability.logging",
            "AuthService" to "route.explorer.observability.auth",
            "MetricCollectingService" to "route.explorer.observability.metrics",
            "BraveService" to "route.explorer.observability.tracing",
            "ThrottlingService" to "route.explorer.observability.throttling",
            "PrometheusMetricCollectingService" to "route.explorer.observability.prometheus",
            "Bucket4jService" to "route.explorer.observability.rateLimit",
        )

    fun summarize(routes: List<ArmeriaRoute>): String {
        val relevantRoutes = routes.filterNot { it.isDocService }
        val parts =
            buildList {
                addAll(observabilitySignals(relevantRoutes.flatMap { it.decorators }.distinct()))
                if (relevantRoutes.any { it.routeMatch == RouteMatch.HEALTH_CHECK }) {
                    add(message("route.explorer.observability.healthCheck"))
                }
            }
        if (parts.isEmpty()) {
            return ""
        }
        return message("route.explorer.observability.summary", parts.joinToString(", "))
    }

    private fun observabilitySignals(decoratorLabels: List<String>): List<String> {
        val signals = linkedSetOf<String>()
        for (label in decoratorLabels) {
            val observabilityKey = observabilityKeyForLabel(label) ?: continue
            signals += message(observabilityKey)
        }
        return signals.toList()
    }

    private fun observabilityKeyForLabel(label: String): String? {
        val normalized = label.removeSuffix("::class.java").removeSuffix("::class").removeSuffix(".class")
        val simpleName = normalized.substringAfterLast('.').removeSuffix("()")
        OBSERVABILITY_KEYS[simpleName]?.let { return it }
        // Also check against already-translated label strings for known decorators.
        for ((decoratorName, key) in OBSERVABILITY_KEYS) {
            val labelKey = decoratorLabelKey(decoratorName)
            if (labelKey != null && label == message(labelKey)) {
                return key
            }
        }
        return null
    }

    private fun decoratorLabelKey(simpleName: String): String? =
        when (simpleName) {
            "LoggingService" -> "route.explorer.decorator.logging"
            "AuthService" -> "route.explorer.decorator.auth"
            "MetricCollectingService" -> "route.explorer.decorator.metrics"
            "BraveService" -> "route.explorer.decorator.brave"
            "ThrottlingService" -> "route.explorer.decorator.throttling"
            "PrometheusMetricCollectingService" -> "route.explorer.decorator.prometheus"
            "Bucket4jService" -> "route.explorer.decorator.bucket4j"
            else -> null
        }
}
