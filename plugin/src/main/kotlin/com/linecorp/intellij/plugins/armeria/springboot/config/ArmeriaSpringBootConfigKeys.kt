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
    val COMPLETION_SUGGESTIONS = listOf(
        "armeria.ports", "armeria.internal-services.port", "armeria.internal-services.include",
        "armeria.compression.enabled", "armeria.enable-auto-injection",
        SERVER_PORT, MANAGEMENT_SERVER_PORT, SPRING_WEB_APPLICATION_TYPE,
    )
    fun isArmeriaRelatedKey(key: String) = key.startsWith(ARMERIA_PREFIX) || key in RELATED_ROOT_KEYS || key.substringBefore('[').split('.').firstOrNull() == "armeria"
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
