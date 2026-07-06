package com.linecorp.intellij.plugins.armeria.explorer

object ArmeriaDocServiceEndpointValidator {
    private val HOST_PATTERN = Regex("""^(?:localhost|[a-zA-Z0-9.\-]+|\[[0-9a-fA-F:.]+\])$""")

    fun validateHost(host: String): String? {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) {
            return "empty"
        }
        if (trimmed.contains('@') || trimmed.contains('/') || trimmed.contains(' ')) {
            return "invalid"
        }
        if (!HOST_PATTERN.matches(trimmed)) {
            return "invalid"
        }
        return null
    }

    fun validatePort(portText: String): Int? {
        val port = portText.trim().toIntOrNull() ?: return null
        if (port !in 1..65_535) {
            return null
        }
        return port
    }

    fun normalizeMountPath(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty() || trimmed == "/") {
            return "/"
        }
        val withLeadingSlash = if (trimmed.startsWith("/")) trimmed else "/$trimmed"
        return withLeadingSlash.trimEnd('/')
    }

    fun buildSpecificationUrl(host: String, port: Int, useHttps: Boolean, mountPath: String): String {
        val scheme = if (useHttps) "https" else "http"
        val normalizedMount = normalizeMountPath(mountPath)
        val base = if (normalizedMount == "/") "" else normalizedMount
        return "$scheme://$host:$port$base/specification.json"
    }
}
