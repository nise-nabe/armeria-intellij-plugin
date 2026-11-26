package com.linecorp.intellij.plugins.armeria.run

object ArmeriaHttpClientEnvironment {
    const val FILE_NAME = "http-client.env.json"
    const val ENV_NAME = "armeria"
    const val HOST_KEY = "host"
    const val PORT_KEY = "port"
    const val SCHEME_KEY = "scheme"
    const val REQUEST_BASE_URL = "{{scheme}}://{{host}}:{{port}}"

    private val ARMERIA_OBJECT = Regex(""""$ENV_NAME"\s*:\s*\{([^{}]*)}""")

    fun content(
        host: String,
        port: Int,
        https: Boolean = false,
    ): String =
        buildString {
            appendLine("{")
            appendLine("  \"$ENV_NAME\": {")
            appendLine("    \"$HOST_KEY\": ${jsonString(host)},")
            appendLine("    \"$PORT_KEY\": \"$port\",")
            appendLine("    \"$SCHEME_KEY\": ${jsonString(scheme(https))}")
            appendLine("  }")
            appendLine("}")
        }

    fun merge(
        existing: String?,
        host: String,
        port: Int,
        https: Boolean = false,
    ): String {
        if (existing.isNullOrBlank()) {
            return content(host, port, https)
        }
        val match = ARMERIA_OBJECT.find(existing)
        if (match == null) {
            return insertArmeriaEnv(existing, host, port, https) ?: content(host, port, https)
        }
        var body = match.groupValues[1]
        body = upsertProperty(body, HOST_KEY, host)
        body = upsertProperty(body, PORT_KEY, port.toString())
        body = upsertProperty(body, SCHEME_KEY, scheme(https))
        return existing.replaceRange(match.groups[1]!!.range, body)
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

    private fun insertArmeriaEnv(
        existing: String,
        host: String,
        port: Int,
        https: Boolean,
    ): String? {
        val rootEnd = existing.lastIndexOf('}')
        if (rootEnd < 0) {
            return null
        }
        val before = existing.substring(0, rootEnd).trimEnd()
        val needsComma = before.lastOrNull()?.let { it != '{' && it != ',' } == true
        val envBlock =
            buildString {
                if (needsComma) {
                    append(',')
                }
                appendLine()
                appendLine("  \"$ENV_NAME\": {")
                appendLine("    \"$HOST_KEY\": ${jsonString(host)},")
                appendLine("    \"$PORT_KEY\": \"$port\",")
                appendLine("    \"$SCHEME_KEY\": ${jsonString(scheme(https))}")
                append("  }")
                appendLine()
            }
        return before + envBlock + existing.substring(rootEnd)
    }

    private fun upsertProperty(
        body: String,
        key: String,
        value: String,
    ): String {
        val pattern = Regex("(\"$key\"\\s*:\\s*)\"([^\"\\\\]*)\"")
        if (pattern.containsMatchIn(body)) {
            return pattern.replace(body, "$1${jsonString(value)}")
        }
        val trimmed = body.trimEnd()
        val separator = if (trimmed.isEmpty() || trimmed.endsWith(',') || trimmed.endsWith('{')) "" else ","
        return "$trimmed$separator\n    \"$key\": ${jsonString(value)}\n  "
    }

    private fun scheme(https: Boolean): String = if (https) "https" else "http"

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
