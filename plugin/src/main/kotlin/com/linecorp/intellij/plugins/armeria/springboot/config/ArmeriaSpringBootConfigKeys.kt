package com.linecorp.intellij.plugins.armeria.springboot.config

import com.linecorp.intellij.plugins.armeria.explorer.spring.SpringArmeriaConfigSemantics
import com.linecorp.intellij.plugins.armeria.message

object ArmeriaSpringBootConfigKeys {
    const val ARMERIA_PREFIX = "armeria."
    const val ARMERIA_ROOT = "armeria"
    const val SERVER_PORT = "server.port"
    const val MANAGEMENT_SERVER_PORT = "management.server.port"
    const val SPRING_WEB_APPLICATION_TYPE = "spring.main.web-application-type"
    const val INTERNAL_SERVICES_INCLUDE = "armeria.internal-services.include"
    val RELATED_ROOT_KEYS = setOf(SERVER_PORT, MANAGEMENT_SERVER_PORT, SPRING_WEB_APPLICATION_TYPE)
    val INTERNAL_SERVICE_INCLUDE_VALUES =
        listOf(
            SpringArmeriaConfigSemantics.ID_DOCS,
            SpringArmeriaConfigSemantics.ID_HEALTH,
            SpringArmeriaConfigSemantics.ID_METRICS,
            SpringArmeriaConfigSemantics.ID_ACTUATOR,
            INCLUDE_ALL,
        )

    private const val INCLUDE_ALL = "all"
    private const val DOC_PREFIX = "springboot.config.doc."

    /**
     * Documented [ArmeriaSettings](https://armeria.dev/docs/advanced-spring-boot-integration) keys plus
     * related Spring Boot keys. Parent keys are listed before children so nested YAML insert text
     * (`ports` under `armeria:`) uses the parent description.
     */
    private val DOCUMENTATION_MESSAGE_KEYS =
        listOf(
            ARMERIA_ROOT,
            "armeria.server-enabled",
            "armeria.ports",
            "armeria.ports.port",
            "armeria.ports.address",
            "armeria.ports.iface",
            "armeria.ports.protocols",
            "armeria.context-path",
            "armeria.docs-path",
            "armeria.health-check-path",
            "armeria.metrics-path",
            "armeria.internal-services",
            INTERNAL_SERVICES_INCLUDE,
            "armeria.internal-services.port",
            "armeria.internal-services.address",
            "armeria.internal-services.iface",
            "armeria.internal-services.protocols",
            "armeria.graceful-shutdown-quiet-period-millis",
            "armeria.graceful-shutdown-timeout-millis",
            "armeria.enable-metrics",
            "armeria.ssl",
            "armeria.ssl.enabled",
            "armeria.ssl.provider",
            "armeria.ssl.client-auth",
            "armeria.ssl.ciphers",
            "armeria.ssl.enabled-protocols",
            "armeria.ssl.key-alias",
            "armeria.ssl.key-password",
            "armeria.ssl.key-store",
            "armeria.ssl.key-store-password",
            "armeria.ssl.key-store-type",
            "armeria.ssl.key-store-provider",
            "armeria.ssl.trust-store",
            "armeria.ssl.trust-store-password",
            "armeria.ssl.trust-store-type",
            "armeria.ssl.trust-store-provider",
            "armeria.compression",
            "armeria.compression.enabled",
            "armeria.compression.mime-types",
            "armeria.compression.excluded-user-agents",
            "armeria.compression.min-response-size",
            "armeria.worker-group",
            "armeria.blocking-task-executor",
            "armeria.max-num-connections",
            "armeria.idle-timeout",
            "armeria.ping-interval",
            "armeria.max-connection-age",
            "armeria.max-num-requests-per-connection",
            "armeria.http2-initial-connection-window-size",
            "armeria.http2-initial-stream-window-size",
            "armeria.http2-max-streams-per-connection",
            "armeria.http2-max-frame-size",
            "armeria.http2-max-header-list-size",
            "armeria.http1-max-initial-line-length",
            "armeria.http1-max-header-size",
            "armeria.http1-max-chunk-size",
            "armeria.access-log",
            "armeria.access-log.type",
            "armeria.access-log.format",
            "armeria.access-logger",
            "armeria.request-timeout",
            "armeria.max-request-length",
            "armeria.verbose-responses",
            "armeria.enable-auto-injection",
            "armeria.athenz",
            "armeria.athenz.zts-uri",
            "armeria.athenz.proxy-uri",
            "armeria.athenz.athenz-private-key",
            "armeria.athenz.athenz-public-key",
            "armeria.athenz.athenz-ca-cert",
            "armeria.athenz.oauth2-keys-path",
            "armeria.athenz.domains",
            "armeria.athenz.jws-policy-support",
            "armeria.athenz.policy-refresh-interval",
            "armeria.athenz.enable-metrics",
            "armeria.athenz.meter-id-prefix",
            SERVER_PORT,
            MANAGEMENT_SERVER_PORT,
            SPRING_WEB_APPLICATION_TYPE,
        ).associateWith { key -> DOC_PREFIX + key }

    val COMPLETION_SUGGESTIONS = DOCUMENTATION_MESSAGE_KEYS.keys.toList()

    fun isArmeriaRelatedKey(key: String): Boolean {
        val canonical = ArmeriaSpringBootConfigSupport.canonicalConfigKey(key)
        return canonical == ARMERIA_ROOT ||
            canonical.startsWith(ARMERIA_PREFIX) ||
            canonical in RELATED_ROOT_KEYS
    }

    fun isRelevantCompletionPath(keyPath: String): Boolean {
        if (keyPath.isEmpty()) {
            return true
        }
        val canonical = ArmeriaSpringBootConfigSupport.canonicalConfigKey(keyPath)
        if (canonical == ARMERIA_ROOT || canonical.startsWith(ARMERIA_PREFIX)) {
            return true
        }
        return RELATED_ROOT_KEYS.any { related ->
            related == canonical || related.startsWith("$canonical.") || canonical.startsWith("$related.")
        }
    }

    fun isIncludeValuePath(keyPath: String): Boolean =
        ArmeriaSpringBootConfigSupport.canonicalConfigKey(keyPath) == INTERNAL_SERVICES_INCLUDE

    /**
     * Text to insert when completing a YAML key under [currentPath] for [suggestion].
     * Under a nested mapping this is the next path segment (not the leaf), so
     * `armeria` + `armeria.internal-services.port` → `internal-services`.
     * At the document root, Armeria keys insert `armeria` rather than a dotted path.
     */
    fun completionInsertText(
        currentPath: String,
        suggestion: String,
    ): String? {
        if (currentPath.isEmpty()) {
            return when {
                suggestion == ARMERIA_ROOT || suggestion.startsWith(ARMERIA_PREFIX) -> ARMERIA_ROOT
                else -> suggestion
            }
        }
        if (!suggestion.startsWith("$currentPath.")) {
            return null
        }
        return suggestion.removePrefix("$currentPath.").substringBefore('.')
    }

    fun documentationFor(key: String): String? {
        var normalized = ArmeriaSpringBootConfigSupport.normalizeIndexedKeyPath(key)
        while (true) {
            DOCUMENTATION_MESSAGE_KEYS[normalized]?.let { return message(it) }
            val parent = normalized.substringBeforeLast('.', "")
            if (parent == normalized) {
                return null
            }
            normalized = parent
        }
    }

    fun documentationForIncludeValue(id: String): String? =
        when (id) {
            SpringArmeriaConfigSemantics.ID_DOCS -> message("springboot.config.doc.include.docs")
            SpringArmeriaConfigSemantics.ID_HEALTH -> message("springboot.config.doc.include.health")
            SpringArmeriaConfigSemantics.ID_METRICS -> message("springboot.config.doc.include.metrics")
            SpringArmeriaConfigSemantics.ID_ACTUATOR -> message("springboot.config.doc.include.actuator")
            INCLUDE_ALL -> message("springboot.config.doc.include.all")
            else -> null
        }
}
