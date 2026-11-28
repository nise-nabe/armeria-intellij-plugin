package com.linecorp.intellij.plugins.armeria.explorer.docservice

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch

object ArmeriaDocServiceSupport {
    const val DEFAULT_BASE_URL = "http://localhost:8080"

    fun docServiceRoutes(routes: List<ArmeriaRoute>): List<ArmeriaRoute> = routes.filter { it.isDocService }

    fun hasDocService(routes: List<ArmeriaRoute>): Boolean = routes.any { it.isDocService }

    fun url(
        route: ArmeriaRoute,
        baseUrl: String = DEFAULT_BASE_URL,
    ): String {
        val normalizedBase = baseUrl.trimEnd('/')
        val path = route.path.takeIf { it.startsWith("/") } ?: "/${route.path}"
        return normalizedBase + path
    }

    fun primaryUrl(
        routes: List<ArmeriaRoute>,
        baseUrl: String = DEFAULT_BASE_URL,
    ): String? = docServiceRoutes(routes).firstOrNull()?.let { url(it, baseUrl) }

    fun listenPortBinding(routes: List<ArmeriaRoute>): Pair<Int, Boolean>? {
        val binding =
            routes
                .filter { it.routeMatch == RouteMatch.LISTEN_PORT }
                .mapNotNull { route ->
                    val port = route.path.removePrefix(":").toIntOrNull() ?: return@mapNotNull null
                    val tokens = route.protocol.split('+').map { it.trim().uppercase() }
                    val https = "HTTPS" in tokens && "HTTP" !in tokens
                    port to https
                }.sortedBy { it.first }
                .firstOrNull()
        return binding
    }

    fun serverBaseUrl(
        routes: List<ArmeriaRoute>,
        defaultBaseUrl: String = DEFAULT_BASE_URL,
    ): String {
        val (port, https) = listenPortBinding(routes) ?: return defaultBaseUrl
        return "${if (https) "https" else "http"}://localhost:$port"
    }

    fun docsBaseUrl(
        routes: List<ArmeriaRoute>,
        lastSyncedBaseUrl: String? = null,
        defaultBaseUrl: String = DEFAULT_BASE_URL,
    ): String? {
        lastSyncedBaseUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.trimEnd('/') }
        return primaryUrl(routes, serverBaseUrl(routes, defaultBaseUrl))
    }

    fun debugFormUrl(
        route: ArmeriaRoute?,
        routes: List<ArmeriaRoute>,
        lastSyncedBaseUrl: String? = null,
        defaultBaseUrl: String = DEFAULT_BASE_URL,
    ): String? {
        if (route == null) {
            return null
        }
        val ref = ArmeriaDocServiceMethodRef.from(route) ?: return null
        val docsBase = docsBaseUrl(routes, lastSyncedBaseUrl, defaultBaseUrl) ?: return null
        return ArmeriaDocServiceDebugFormUrl.build(docsBase, ref)
    }
}
