package com.linecorp.intellij.plugins.armeria.run

object ArmeriaHttpClientEnvironment {
    const val FILE_NAME = "http-client.env.json"
    const val ENV_NAME = "armeria"
    const val HOST_KEY = "host"
    const val PORT_KEY = "port"
    const val REQUEST_BASE_URL = "http://{{host}}:{{port}}"

    fun content(
        host: String,
        port: Int,
    ): String =
        buildString {
            appendLine("{")
            appendLine("  \"$ENV_NAME\": {")
            appendLine("    \"$HOST_KEY\": ${jsonString(host)},")
            appendLine("    \"$PORT_KEY\": \"$port\"")
            appendLine("  }")
            appendLine("}")
        }

    fun merge(
        existing: String?,
        host: String,
        port: Int,
    ): String {
        if (existing.isNullOrBlank()) {
            return content(host, port)
        }
        val updatedHost = replaceJsonProperty(existing, HOST_KEY, host)
        if (updatedHost != null) {
            val updatedPort = replaceJsonProperty(updatedHost, PORT_KEY, port.toString())
            if (updatedPort != null) {
                return updatedPort
            }
        }
        return content(host, port)
    }

    fun requestBaseUrl(
        requested: String,
        defaultUrl: String,
        envFileExists: Boolean,
    ): String =
        if (requested == defaultUrl && envFileExists) {
            REQUEST_BASE_URL
        } else {
            requested
        }

    private fun replaceJsonProperty(
        json: String,
        key: String,
        value: String,
    ): String? {
        val pattern = Regex("(\"$key\"\\s*:\\s*)\"([^\"\\\\]*)\"")
        if (!pattern.containsMatchIn(json)) {
            return null
        }
        return pattern.replaceFirst(json, "$1${jsonString(value)}")
    }

    private fun jsonString(value: String): String =
        buildString {
            append('"')
            for (char in value) {
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
}
