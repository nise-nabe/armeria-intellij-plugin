package com.linecorp.intellij.plugins.armeria.client

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteAnalysisCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import java.net.URI

internal object ArmeriaClientRouteLinkSupport {
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
        )

    fun matches(
        clientType: String,
        uri: String,
        routeProtocol: String,
        routePath: String,
        virtualHostName: String = "",
        routeMatch: RouteMatch = RouteMatch.ANNOTATED_HTTP,
    ): Boolean {
        if (routeMatch !in MATCHABLE_ROUTE_MATCHES) {
            return false
        }
        val protocol = ClientProtocol.fromPresentableName(clientType) ?: return false
        if (!protocol.matchesRouteProtocol(routeProtocol)) {
            return false
        }
        val parts = parseClientUri(uri)
        if (!hostsCompatible(parts.host, virtualHostName)) {
            return false
        }
        return pathsOverlap(parts.path, routePath)
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
                path = normalizePath(uri.path.orEmpty().ifBlank { "/" }),
            )
        } catch (_: Exception) {
            ClientUriParts(host = null, path = normalizePath(trimmed))
        }
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
}
