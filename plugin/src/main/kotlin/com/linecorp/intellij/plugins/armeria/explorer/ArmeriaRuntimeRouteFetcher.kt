package com.linecorp.intellij.plugins.armeria.explorer

import java.net.HttpURLConnection
import java.net.URI

data class RuntimeRoute(
    val path: String,
    val method: String,
)

object ArmeriaRuntimeRouteFetcher {
    private val PATH_PATTERN = Regex(""""path"\s*:\s*"([^"]+)"""")
    private val METHOD_PATTERN = Regex(""""(get|post|put|delete|patch|head|options|trace)"\s*:\s*\{""", RegexOption.IGNORE_CASE)

    fun fetch(host: String = "localhost", port: Int = 8080): List<RuntimeRoute> {
        val specification = readUrl("http://$host:$port/internal/docs/specification.json") ?: return emptyList()
        val paths = PATH_PATTERN.findAll(specification).map { it.groupValues[1] }.toList()
        if (paths.isNotEmpty()) {
            return paths.map { RuntimeRoute(it, "HTTP") }
        }
        val methods = METHOD_PATTERN.findAll(specification).map { it.groupValues[1].uppercase() }.toList()
        return methods.mapIndexed { index, method ->
            RuntimeRoute(path = "/runtime/$index", method = method)
        }
    }

    private fun readUrl(url: String): String? {
        return try {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 2_000
            connection.readTimeout = 2_000
            connection.requestMethod = "GET"
            if (connection.responseCode !in 200..299) {
                return null
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }
}
