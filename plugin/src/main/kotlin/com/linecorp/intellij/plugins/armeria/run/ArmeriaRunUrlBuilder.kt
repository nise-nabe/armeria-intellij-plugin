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
    private val INTERNAL_SERVICE_PORT = Regex("""· :(\d+)""")
    private val DEFAULT_APPLICATION_FILES = setOf("application.properties", "application.yml", "application.yaml")

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
        val docsRoute = routes.firstOrNull { it.isDocService }
        val healthRoute =
            routes.firstOrNull { it.routeMatch == RouteMatch.HEALTH_CHECK }
                ?: routes.firstOrNull { route ->
                    route.routeMatch == RouteMatch.CONFIG &&
                        route.path.contains("health", ignoreCase = true)
                }
        val metricsRoute =
            routes.firstOrNull { route ->
                route.target.contains("PrometheusExpositionService") ||
                    (
                        route.routeMatch == RouteMatch.CONFIG &&
                            route.path.contains("metrics", ignoreCase = true)
                    )
            }
        return ArmeriaRunServiceUrls(
            docService = docsRoute?.let { serviceUrl(listenFor(it, listen), it.path, trailingSlash = true) },
            health = healthRoute?.let { serviceUrl(listenFor(it, listen), it.path) },
            metrics = metricsRoute?.let { serviceUrl(listenFor(it, listen), it.path) },
            listen = listen,
        )
    }

    fun listenPortFromSpringRoutes(routes: List<ArmeriaRoute>): ArmeriaListenEndpoint? {
        val candidates =
            routes.mapNotNull { route ->
                if (route.routeMatch != RouteMatch.LISTEN_PORT) {
                    return@mapNotNull null
                }
                val port = parsePort(springPortPath(route.path) ?: return@mapNotNull null) ?: return@mapNotNull null
                SpringPortCandidate(
                    endpoint = ArmeriaListenEndpoint(port, https = ArmeriaSessionProtocols.isHttpsOnly(route.protocol)),
                    defaultApplicationFile = isDefaultApplicationConfigName(route.pointer.containingFile?.name),
                )
            }
        return candidates.firstOrNull { it.defaultApplicationFile }?.endpoint
            ?: candidates.firstOrNull()?.endpoint
    }

    fun parsePort(raw: String): Int? {
        val trimmed = raw.trim()
        trimmed.toIntOrNull()?.let { return it.takeIf(::isValidPort) }
        val placeholderDefault = PLACEHOLDER_DEFAULT_PORT.matchEntire(trimmed) ?: return null
        return placeholderDefault.groupValues[1].toIntOrNull()?.takeIf(::isValidPort)
    }

    private fun listenFor(
        route: ArmeriaRoute,
        fallback: ArmeriaListenEndpoint,
    ): ArmeriaListenEndpoint {
        val internal =
            INTERNAL_SERVICE_PORT
                .find(route.target)
                ?.groupValues
                ?.get(1)
                ?.let(::parsePort)
        return if (internal != null) {
            ArmeriaListenEndpoint(internal, https = fallback.https)
        } else {
            fallback
        }
    }

    internal fun isDefaultApplicationConfigName(name: String?): Boolean = name != null && name in DEFAULT_APPLICATION_FILES

    private fun springPortPath(path: String): String? = SPRING_PORT_PATH.matchEntire(path)?.groupValues?.get(1)

    private fun isValidPort(port: Int): Boolean = port in 1..65535

    private data class SpringPortCandidate(
        val endpoint: ArmeriaListenEndpoint,
        val defaultApplicationFile: Boolean,
    )
}
