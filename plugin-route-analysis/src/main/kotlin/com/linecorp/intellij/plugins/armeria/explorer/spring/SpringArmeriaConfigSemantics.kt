package com.linecorp.intellij.plugins.armeria.explorer.spring

/**
 * Shared Spring Boot `armeria.*` config semantics used by both the properties parser and the
 * optional YAML PSI reader. Keep this free of format-specific PSI so either path can depend on it.
 */
internal object SpringArmeriaConfigSemantics {
    const val DEFAULT_DOCS_PATH = "/internal/docs"
    const val DEFAULT_HEALTH_PATH = "/internal/healthcheck"
    const val DEFAULT_METRICS_PATH = "/internal/metrics"

    /** Canonical internal-service ids — also used by [ArmeriaSpringConfigRouteCollector] specs. */
    const val ID_DOCS = "docs"
    const val ID_HEALTH = "health"
    const val ID_METRICS = "metrics"
    const val ID_ACTUATOR = "actuator"

    val INTERNAL_SERVICE_IDS: Set<String> = setOf(ID_DOCS, ID_HEALTH, ID_METRICS, ID_ACTUATOR)

    fun expandIncludes(raw: Set<String>): Set<String> {
        if (raw.any { it.equals("all", ignoreCase = true) }) {
            return INTERNAL_SERVICE_IDS
        }
        return raw.map { it.lowercase() }.filter { it in INTERNAL_SERVICE_IDS }.toSet()
    }

    fun parseIncludeTokens(raw: String): Set<String> = splitScalarList(raw).map { it.lowercase() }.toSet()

    fun normalizeProtocols(tokens: Iterable<String>): List<String> =
        tokens
            .map { it.uppercase() }
            .filter { it.isNotEmpty() }
            .distinct()
            .ifEmpty { listOf("HTTP") }

    fun splitScalarList(raw: String): List<String> {
        val normalized =
            raw
                .trim()
                .removePrefix("[")
                .removeSuffix("]")
                .trim()
        if (normalized.isEmpty()) {
            return emptyList()
        }
        return normalized
            .split(',', ' ', '\t')
            .map(::trimQuotes)
            .filter { it.isNotEmpty() }
    }

    fun trimQuotes(raw: String): String = raw.trim().removeSurrounding("\"").removeSurrounding("'")
}
