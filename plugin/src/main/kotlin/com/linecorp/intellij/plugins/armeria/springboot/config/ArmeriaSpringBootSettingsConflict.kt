package com.linecorp.intellij.plugins.armeria.springboot.config

import com.linecorp.intellij.plugins.armeria.explorer.spring.SpringArmeriaConfigSemantics
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport

internal object ArmeriaSpringBootSettingsConflict {
    const val SERVER_ENABLED_KEY = "armeria.server-enabled"
    const val PORTS_KEY = "armeria.ports"
    const val DOCS_PATH_KEY = "armeria.docs-path"
    const val HEALTH_PATH_KEY = "armeria.health-check-path"
    const val METRICS_PATH_KEY = "armeria.metrics-path"

    data class Finding(
        val kind: Kind,
        val highlightPath: String,
        val includeId: String? = null,
        val pathKey: String? = null,
    )

    enum class Kind {
        PORT_CONFLICT,
        MISSING_INTERNAL_SERVICE,
    }

    private data class InternalServiceSpec(
        val id: String,
        val pathKey: String,
        val beanFqns: Set<String>,
    )

    private val INTERNAL_SERVICE_SPECS =
        listOf(
            InternalServiceSpec(
                id = SpringArmeriaConfigSemantics.ID_DOCS,
                pathKey = DOCS_PATH_KEY,
                beanFqns =
                    setOf(
                        ArmeriaRouteSupport.DOC_SERVICE_CONFIGURATOR_CLASS,
                        ArmeriaRouteSupport.DOC_SERVICE_CLASS,
                    ),
            ),
            InternalServiceSpec(
                id = SpringArmeriaConfigSemantics.ID_HEALTH,
                pathKey = HEALTH_PATH_KEY,
                beanFqns = setOf(ArmeriaRouteSupport.HEALTH_CHECK_SERVICE_CONFIGURATOR_CLASS),
            ),
            InternalServiceSpec(
                id = SpringArmeriaConfigSemantics.ID_METRICS,
                pathKey = METRICS_PATH_KEY,
                beanFqns = setOf(ArmeriaRouteSupport.METRIC_COLLECTING_SERVICE_CONFIGURATOR_CLASS),
            ),
        )

    fun findings(
        entries: Map<String, String>,
        presentBeanFqns: Set<String>?,
    ): List<Finding> {
        val findings = mutableListOf<Finding>()
        if (isPortConflict(entries)) {
            findings +=
                Finding(
                    kind = Kind.PORT_CONFLICT,
                    highlightPath = ArmeriaSpringBootConfigKeys.SERVER_PORT,
                )
        }
        if (presentBeanFqns != null) {
            findings += missingInternalServices(entries, presentBeanFqns)
        }
        return findings
    }

    fun isPortConflict(entries: Map<String, String>): Boolean = armeriaWouldBind(entries) && springWebWouldBind(entries)

    private fun armeriaWouldBind(entries: Map<String, String>): Boolean {
        val enabled = lastValue(entries, SERVER_ENABLED_KEY)?.trim()
        if (enabled.equals("false", ignoreCase = true)) {
            return false
        }
        val hasPorts =
            entries.keys.any { key ->
                val normalized = ArmeriaSpringBootConfigSupport.normalizeIndexedKeyPath(key)
                normalized == PORTS_KEY || normalized.startsWith("$PORTS_KEY.")
            }
        return hasPorts || enabled.equals("true", ignoreCase = true)
    }

    private fun springWebWouldBind(entries: Map<String, String>): Boolean {
        val webType = lastValue(entries, ArmeriaSpringBootConfigKeys.SPRING_WEB_APPLICATION_TYPE)?.trim()
        if (webType.equals("none", ignoreCase = true)) {
            return false
        }
        val port = lastValue(entries, ArmeriaSpringBootConfigKeys.SERVER_PORT)?.trim() ?: return false
        val parsed = port.toIntOrNull() ?: return false
        return parsed > 0
    }

    private fun missingInternalServices(
        entries: Map<String, String>,
        presentBeanFqns: Set<String>,
    ): List<Finding> {
        val included = includedServiceIds(entries)
        if (included.isEmpty()) {
            return emptyList()
        }
        return INTERNAL_SERVICE_SPECS.mapNotNull { spec ->
            if (spec.id !in included) {
                return@mapNotNull null
            }
            if (hasNonBlank(entries, spec.pathKey)) {
                return@mapNotNull null
            }
            if (spec.beanFqns.any { it in presentBeanFqns }) {
                return@mapNotNull null
            }
            Finding(
                kind = Kind.MISSING_INTERNAL_SERVICE,
                highlightPath = ArmeriaSpringBootConfigKeys.INTERNAL_SERVICES_INCLUDE,
                includeId = spec.id,
                pathKey = spec.pathKey,
            )
        }
    }

    private fun includedServiceIds(entries: Map<String, String>): Set<String> {
        val tokens = mutableSetOf<String>()
        for ((key, value) in entries) {
            if (ArmeriaSpringBootConfigKeys.isIncludeValuePath(key)) {
                tokens += SpringArmeriaConfigSemantics.parseIncludeTokens(value)
            }
        }
        return SpringArmeriaConfigSemantics.expandIncludes(tokens)
    }

    private fun hasNonBlank(
        entries: Map<String, String>,
        normalizedKey: String,
    ): Boolean =
        entries.any { (key, value) ->
            ArmeriaSpringBootConfigSupport.normalizeIndexedKeyPath(key) == normalizedKey &&
                value.trim().isNotEmpty()
        }

    private fun lastValue(
        entries: Map<String, String>,
        normalizedKey: String,
    ): String? =
        entries.entries
            .lastOrNull { ArmeriaSpringBootConfigSupport.normalizeIndexedKeyPath(it.key) == normalizedKey }
            ?.value
}
