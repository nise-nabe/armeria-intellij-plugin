package com.linecorp.intellij.plugins.armeria.springboot.config

import com.linecorp.intellij.plugins.armeria.message

object ArmeriaSpringBootConfigKeys {
    const val ARMERIA_PREFIX = "armeria."
    const val SERVER_PORT = "server.port"
    const val MANAGEMENT_SERVER_PORT = "management.server.port"
    const val SPRING_WEB_APPLICATION_TYPE = "spring.main.web-application-type"
    val RELATED_ROOT_KEYS = setOf(SERVER_PORT, MANAGEMENT_SERVER_PORT, SPRING_WEB_APPLICATION_TYPE)
    private val DOCUMENTATION_MESSAGE_KEYS = mapOf(
        "armeria.ports" to "springboot.config.doc.armeria.ports",
        "armeria.internal-services.port" to "springboot.config.doc.armeria.internal-services.port",
        "armeria.internal-services.include" to "springboot.config.doc.armeria.internal-services.include",
        "armeria.compression.enabled" to "springboot.config.doc.armeria.compression.enabled",
        "armeria.enable-auto-injection" to "springboot.config.doc.armeria.enable-auto-injection",
        SERVER_PORT to "springboot.config.doc.server.port",
        MANAGEMENT_SERVER_PORT to "springboot.config.doc.management.server.port",
        SPRING_WEB_APPLICATION_TYPE to "springboot.config.doc.spring.main.web-application-type",
    )
    val COMPLETION_SUGGESTIONS = DOCUMENTATION_MESSAGE_KEYS.keys.toList()

    fun isArmeriaRelatedKey(key: String): Boolean {
        val normalized = ArmeriaSpringBootConfigSupport.normalizeIndexedKeyPath(key)
        return normalized == "armeria" ||
            normalized.startsWith(ARMERIA_PREFIX) ||
            normalized in RELATED_ROOT_KEYS
    }

    fun isRelevantCompletionPath(keyPath: String): Boolean {
        if (keyPath.isEmpty()) {
            return true
        }
        val normalized = ArmeriaSpringBootConfigSupport.normalizeIndexedKeyPath(keyPath)
        if (normalized == "armeria" || normalized.startsWith(ARMERIA_PREFIX)) {
            return true
        }
        return RELATED_ROOT_KEYS.any { related ->
            related == normalized || related.startsWith("$normalized.") || normalized.startsWith("$related.")
        }
    }

    /**
     * Text to insert when completing a YAML key under [currentPath] for [suggestion].
     * Under a nested mapping this is the next path segment (not the leaf), so
     * `armeria` + `armeria.internal-services.port` → `internal-services`.
     */
    fun completionInsertText(currentPath: String, suggestion: String): String? {
        if (currentPath.isEmpty()) {
            return suggestion
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
}
