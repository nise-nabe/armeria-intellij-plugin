package com.linecorp.intellij.plugins.armeria.explorer

import java.util.Collections

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

    val INTERNAL_SERVICE_IDS: Set<String> = Collections.unmodifiableSet(
        linkedSetOf(ID_DOCS, ID_HEALTH, ID_METRICS, ID_ACTUATOR),
    )

    fun expandIncludes(raw: Set<String>): Set<String> {
        if (raw.any { it.equals("all", ignoreCase = true) }) {
            return INTERNAL_SERVICE_IDS
        }
        return raw.map { it.lowercase() }.filter { it in INTERNAL_SERVICE_IDS }.toSet()
    }

    fun parseIncludeTokens(raw: String): Set<String> =
        splitScalarList(raw).map { it.lowercase() }.toSet()

    fun splitScalarList(raw: String): List<String> {
        val normalized = raw.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .trim()
        if (normalized.isEmpty()) {
            return emptyList()
        }
        return normalized.split(',', ' ', '\t')
            .map { stripInlineComment(it).trimQuotes() }
            .filter { it.isNotEmpty() }
    }

    private val INLINE_COMMENT = Regex("""\s+#.*$""")

    private fun stripInlineComment(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return trimmed
        }
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\"")) ||
            (trimmed.startsWith("'") && trimmed.endsWith("'"))
        ) {
            return trimmed
        }
        if (trimmed.startsWith("#")) {
            return ""
        }
        return trimmed.replace(INLINE_COMMENT, "").trimEnd()
    }

    private fun String.trimQuotes(): String =
        trim().removeSurrounding("\"").removeSurrounding("'")
}
