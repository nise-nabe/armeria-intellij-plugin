package com.linecorp.intellij.plugins.armeria.run

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch

data class ArmeriaRunServiceUrls(
    val docService: String? = null,
    val health: String? = null,
    val metrics: String? = null,
    val listen: ArmeriaListenEndpoint? = null,
) {
    val isEmpty: Boolean
        get() = docService == null && health == null && metrics == null
}

object ArmeriaRunUrlBuilder {
    const val LOOPBACK_HOST = "127.0.0.1"

    private val SPRING_PORT_PATH = Regex("""^:(.+)$""")
    private val PLACEHOLDER_DEFAULT_PORT = Regex("""\$\{[^:}]+:(\d+)\}""")
    private val HTTP_PROTOCOLS = setOf("HTTP", "H1C", "H2C", "H1")

    fun baseUrl(listen: ArmeriaListenEndpoint): String {
        val scheme = if (listen.https) "https" else "http"
        return "$scheme://$LOOPBACK_HOST:${listen.port}"
    }

    fun serviceUrl(
        listen: ArmeriaListenEndpoint,
        path: String,
        trailingSlash: Boolean = false,
    ): String {
        val withLeadingSlash = if (path.startsWith("/")) path else "/$path"
        val normalized =
            when {
                trailingSlash && !withLeadingSlash.endsWith("/") -> "$withLeadingSlash/"
                else -> withLeadingSlash
            }
        return baseUrl(listen) + normalized
    }

    fun fromRoutes(
        listen: ArmeriaListenEndpoint?,
        routes: List<ArmeriaRoute>,
    ): ArmeriaRunServiceUrls {
        if (listen == null) {
            return ArmeriaRunServiceUrls()
        }
        val docsPath = routes.firstOrNull { it.isDocService }?.path
        val healthPath =
            routes.firstOrNull { it.routeMatch == RouteMatch.HEALTH_CHECK }?.path
                ?: routes
                    .firstOrNull { route ->
                        route.routeMatch == RouteMatch.CONFIG &&
                            route.path.contains("health", ignoreCase = true)
                    }?.path
        val metricsPath =
            routes
                .firstOrNull { route ->
                    route.target.contains("PrometheusExpositionService") ||
                        (
                            route.routeMatch == RouteMatch.CONFIG &&
                                route.path.contains("metrics", ignoreCase = true)
                        )
                }?.path
        return ArmeriaRunServiceUrls(
            docService = docsPath?.let { serviceUrl(listen, it, trailingSlash = true) },
            health = healthPath?.let { serviceUrl(listen, it) },
            metrics = metricsPath?.let { serviceUrl(listen, it) },
            listen = listen,
        )
    }

    fun listenPortFromSpringRoutes(routes: List<ArmeriaRoute>): ArmeriaListenEndpoint? {
        for (route in routes) {
            val port = parsePort(springPortPath(route.path) ?: continue) ?: continue
            return ArmeriaListenEndpoint(port, https = isHttpsOnly(route.protocol))
        }
        return null
    }

    fun parsePort(raw: String): Int? {
        val trimmed = raw.trim()
        trimmed.toIntOrNull()?.let { return it.takeIf(::isValidPort) }
        val placeholderDefault = PLACEHOLDER_DEFAULT_PORT.matchEntire(trimmed) ?: return null
        return placeholderDefault.groupValues[1].toIntOrNull()?.takeIf(::isValidPort)
    }

    private fun springPortPath(path: String): String? = SPRING_PORT_PATH.matchEntire(path)?.groupValues?.get(1)

    private fun isHttpsOnly(protocol: String): Boolean {
        val protocols =
            protocol
                .split(',')
                .map { it.trim().uppercase() }
                .filter { it.isNotEmpty() }
        if (protocols.isEmpty()) {
            return false
        }
        val hasHttp = protocols.any { it in HTTP_PROTOCOLS }
        val hasHttps = protocols.any { it == "HTTPS" || it == "H2" }
        return hasHttps && !hasHttp
    }

    private fun isValidPort(port: Int): Boolean = port in 1..65535
}
