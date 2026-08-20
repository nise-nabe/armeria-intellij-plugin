package com.linecorp.intellij.plugins.armeria.client

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteAnalysisCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.message
import java.net.URI

internal object ArmeriaClientRouteLinkSupport {
    private val HTTP_LIKE_SCHEME_TOKENS =
        setOf("http", "https", "ws", "wss", "h1", "h1c", "h2", "h2c", "none")

    private val MATCHABLE_ROUTE_MATCHES =
        setOf(
            RouteMatch.ANNOTATED_HTTP,
            RouteMatch.ANNOTATED_SERVICE,
            RouteMatch.SERVICE,
            RouteMatch.SERVICE_UNDER,
            RouteMatch.ROUTE_FLUENT,
            RouteMatch.DELEGATED,
            RouteMatch.FILE_SERVICE,
            RouteMatch.HEALTH_CHECK,
            RouteMatch.NON_HTTP,
            RouteMatch.CONFIG,
        )

    data class ClientUriParts(
        val host: String?,
        val path: String,
    )

    fun matchingRoutes(
        project: Project,
        endpoint: ArmeriaClientEndpoint,
    ): List<ArmeriaRoute> {
        if (DumbService.isDumb(project)) {
            return emptyList()
        }
        return try {
            ArmeriaRouteAnalysisCollector
                .collect(project)
                .filter { matches(endpoint, it) }
                .sortedWith(
                    compareByDescending<ArmeriaRoute> { it.moduleName == endpoint.moduleName }
                        .thenBy { it.path }
                        .thenBy { it.httpMethod },
                )
        } catch (_: IndexNotReadyException) {
            emptyList()
        }
    }

    fun matchingClients(
        project: Project,
        route: ArmeriaRoute,
    ): List<ArmeriaClientEndpoint> {
        if (DumbService.isDumb(project)) {
            return emptyList()
        }
        return try {
            ArmeriaClientCollector
                .collect(project)
                .filter { matches(it, route) }
                .sortedWith(
                    compareByDescending<ArmeriaClientEndpoint> { it.moduleName == route.moduleName }
                        .thenBy { it.uri }
                        .thenBy { it.clientType },
                )
        } catch (_: IndexNotReadyException) {
            emptyList()
        }
    }

    fun matches(
        endpoint: ArmeriaClientEndpoint,
        route: ArmeriaRoute,
    ): Boolean =
        matches(
            clientType = endpoint.clientType,
            uri = endpoint.uri,
            routeProtocol = route.protocol,
            routePath = route.path,
            virtualHostName = route.virtualHostName,
            routeMatch = route.routeMatch,
            requestPath = endpoint.requestPath,
            httpMethod = endpoint.httpMethod,
            routeHttpMethod = route.httpMethod,
        )

    fun matches(
        clientType: String,
        uri: String,
        routeProtocol: String,
        routePath: String,
        virtualHostName: String = "",
        routeMatch: RouteMatch = RouteMatch.ANNOTATED_HTTP,
        requestPath: String? = null,
        httpMethod: String = "",
        routeHttpMethod: String = "",
    ): Boolean {
        if (routeMatch !in MATCHABLE_ROUTE_MATCHES) {
            return false
        }
        val protocol = ClientProtocol.fromPresentableName(clientType) ?: return false
        if (!protocol.matchesRouteProtocol(routeProtocol)) {
            return false
        }
        if (httpMethod.isNotBlank() &&
            routeHttpMethod.isNotBlank() &&
            !httpMethod.equals(routeHttpMethod, ignoreCase = true)
        ) {
            return false
        }
        val factoryParts = parseClientUri(uri)
        val requestParts = requestPath?.takeIf { it.isNotBlank() }?.let(::partsForRequestPath)
        val clientHost =
            when {
                requestParts?.host != null -> requestParts.host
                !requestPath.isNullOrBlank() && !isHttpLikeClientUri(uri) -> null
                else -> factoryParts.host
            }
        if (!hostsCompatible(clientHost, virtualHostName)) {
            return false
        }
        val clientPath = requestParts?.path ?: factoryParts.path
        return pathsOverlap(clientPath, routePath)
    }

    private fun partsForRequestPath(requestPath: String): ClientUriParts {
        if (isAbsoluteHttpUri(requestPath)) {
            return parseClientUri(requestPath)
        }
        return ClientUriParts(host = null, path = normalizePath(requestPath))
    }

    fun isAbsoluteHttpUri(raw: String): Boolean {
        val trimmed = raw.trim()
        val schemeSeparator = trimmed.indexOf("://")
        if (schemeSeparator <= 0) {
            return false
        }
        return isHttpLikeScheme(trimmed.substring(0, schemeSeparator))
    }

    fun pathForMatching(requestPath: String): String = partsForRequestPath(requestPath).path

    fun httpOrigin(raw: String): String? {
        val trimmed = raw.trim()
        val schemeSeparator = trimmed.indexOf("://")
        if (schemeSeparator <= 0) {
            return null
        }
        if (!isHttpLikeScheme(trimmed.substring(0, schemeSeparator))) {
            return null
        }
        return try {
            val parsed = URI(trimmed)
            val host = parsed.host ?: return null
            buildString {
                append(parsed.scheme ?: "http")
                append("://")
                append(host)
                if (parsed.port >= 0) {
                    append(':')
                    append(parsed.port)
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun isHttpLikeClientUri(raw: String): Boolean {
        val trimmed = raw.trim()
        val schemeSeparator = trimmed.indexOf("://")
        if (schemeSeparator <= 0) {
            return true
        }
        return isHttpLikeScheme(trimmed.substring(0, schemeSeparator))
    }

    fun parseClientUri(raw: String): ClientUriParts {
        val trimmed = raw.trim()
        if (trimmed.startsWith("/")) {
            return ClientUriParts(host = null, path = normalizePath(trimmed))
        }
        if (!trimmed.contains("://") && !trimmed.contains('/') && trimmed.contains('.')) {
            return ClientUriParts(host = trimmed.trimEnd('.').lowercase(), path = "/")
        }
        return try {
            val uri = URI(trimmed)
            ClientUriParts(
                host = uri.host?.lowercase(),
                path =
                    if (isHttpLikeScheme(uri.scheme)) {
                        normalizePath(uri.path.orEmpty().ifBlank { "/" })
                    } else {
                        "/"
                    },
            )
        } catch (_: Exception) {
            ClientUriParts(host = null, path = normalizePath(trimmed))
        }
    }

    internal fun isHttpLikeScheme(scheme: String?): Boolean {
        if (scheme.isNullOrBlank()) {
            return true
        }
        return scheme.lowercase().split('+', '-').any { it in HTTP_LIKE_SCHEME_TOKENS }
    }

    fun normalizePath(path: String): String {
        val withoutQuery = path.substringBefore('?').substringBefore('#')
        val withSlash = if (withoutQuery.startsWith("/")) withoutQuery else "/$withoutQuery"
        return when {
            withSlash.length > 1 && withSlash.endsWith("/") -> withSlash.dropLast(1)
            withSlash.isBlank() -> "/"
            else -> withSlash
        }
    }

    fun pathsOverlap(
        clientPath: String,
        routePath: String,
    ): Boolean {
        val client = normalizePath(clientPath)
        val route = normalizePath(routePath)
        if (client == "/" || route == "/") {
            return false
        }
        return client == route ||
            client.startsWith("$route/") ||
            route.startsWith("$client/")
    }

    fun hostsCompatible(
        clientHost: String?,
        routeHost: String,
    ): Boolean {
        if (clientHost.isNullOrBlank() || routeHost.isBlank()) {
            return true
        }
        return clientHost.equals(routeHost, ignoreCase = true)
    }

    fun matchingRouteTooltip(routes: List<ArmeriaRoute>): String {
        val first = routes.first()
        return if (routes.size == 1) {
            message(
                "marker.client.route.tooltip",
                StringUtil.escapeXmlEntities(first.methodLabel),
                StringUtil.escapeXmlEntities(first.path),
            )
        } else {
            message("marker.client.route.tooltipMultiple", routes.size)
        }
    }
}
