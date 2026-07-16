package com.linecorp.intellij.plugins.armeria.springboot.config

object ArmeriaSpringBootConfigKeys {
    const val ARMERIA_PREFIX = "armeria."
    const val SERVER_PORT = "server.port"
    const val MANAGEMENT_SERVER_PORT = "management.server.port"
    const val SPRING_WEB_APPLICATION_TYPE = "spring.main.web-application-type"
    val RELATED_ROOT_KEYS = setOf(SERVER_PORT, MANAGEMENT_SERVER_PORT, SPRING_WEB_APPLICATION_TYPE)
    val DOCUMENTATION = mapOf(
        "armeria.ports" to "Listening ports for the Armeria server (list of port/protocol entries).",
        "armeria.internal-services.port" to "Port for Armeria internal services (docs, health, metrics, actuator).",
        "armeria.internal-services.include" to "Comma-separated internal service IDs: docs, health, metrics, actuator, or all.",
        "armeria.compression.enabled" to "Whether response compression is enabled.",
        "armeria.enable-auto-injection" to "Whether Armeria auto-injects Spring beans into the server.",
        SERVER_PORT to "Spring Boot embedded web server port. Use -1 to disable Tomcat when Armeria serves HTTP.",
        MANAGEMENT_SERVER_PORT to "Spring Boot Actuator management port; may overlap with Armeria internal services.",
        SPRING_WEB_APPLICATION_TYPE to "Set to none to prevent Spring from starting an embedded web server.",
    )
    val COMPLETION_SUGGESTIONS = DOCUMENTATION.keys.toList()

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
            DOCUMENTATION[normalized]?.let { return it }
            val parent = normalized.substringBeforeLast('.', "")
            if (parent == normalized) {
                return null
            }
            normalized = parent
        }
    }
}
