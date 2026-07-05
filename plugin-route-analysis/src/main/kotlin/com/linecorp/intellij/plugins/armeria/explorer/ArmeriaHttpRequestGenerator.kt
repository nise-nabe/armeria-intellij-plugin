package com.linecorp.intellij.plugins.armeria.explorer

import java.util.Locale

object ArmeriaHttpRequestGenerator {
    const val DEFAULT_BASE_URL = "http://localhost:8080"

    private val NON_SLUG_CHARACTERS = Regex("[^a-zA-Z0-9._-]")
    private val BRACE_PATH_VARIABLE = Regex("""\{([^}]+)}""")
    private val COLON_PATH_VARIABLE = Regex(""":([A-Za-z_][A-Za-z0-9_]*)""")

    fun supports(route: ArmeriaRoute): Boolean {
        return when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP -> route.httpMethod.isNotBlank()
            RouteMatch.SERVICE, RouteMatch.SERVICE_UNDER, RouteMatch.HEALTH_CHECK, RouteMatch.ROUTE_FLUENT -> true
            RouteMatch.RUNTIME -> route.httpMethod.isNotBlank()
            RouteMatch.ANNOTATED_SERVICE -> route.annotatedServiceHasPathPrefix
            RouteMatch.NON_HTTP -> route.protocol.equals("gRPC", ignoreCase = true)
            RouteMatch.FILE_SERVICE, RouteMatch.VIRTUAL_HOST, RouteMatch.ROUTE_DECORATOR, RouteMatch.DECORATOR_UNDER ->
                false
        }
    }

    fun httpMethod(route: ArmeriaRoute): String {
        return when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP, RouteMatch.RUNTIME -> route.httpMethod
            RouteMatch.SERVICE, RouteMatch.SERVICE_UNDER, RouteMatch.HEALTH_CHECK,
            RouteMatch.ANNOTATED_SERVICE, RouteMatch.ROUTE_FLUENT,
            -> route.httpMethod.ifBlank { "GET" }
            RouteMatch.NON_HTTP -> "POST"
            RouteMatch.FILE_SERVICE, RouteMatch.VIRTUAL_HOST, RouteMatch.ROUTE_DECORATOR, RouteMatch.DECORATOR_UNDER ->
                error("Unsupported route match: ${route.routeMatch}")
        }
    }

    fun fileName(route: ArmeriaRoute): String {
        if (route.routeMatch == RouteMatch.NON_HTTP) {
            val slug = pathSlug(route.path)
            return "armeria-grpc-$slug.http"
        }
        val method = httpMethod(route).lowercase(Locale.ROOT)
        return "armeria-$method-${pathSlug(route.path)}.http"
    }

    fun requestText(route: ArmeriaRoute, baseUrl: String = DEFAULT_BASE_URL): String {
        if (route.routeMatch == RouteMatch.NON_HTTP) {
            return grpcRequestText(route, baseUrl)
        }
        val method = httpMethod(route)
        val resolvedPath = pathWithPlaceholders(route.path)
        return buildString {
            appendLine("### ${route.path}")
            appendLine("$method $baseUrl$resolvedPath")
            appendLine("Accept: application/json")
            appendLine()
        }
    }

    private fun grpcRequestText(route: ArmeriaRoute, baseUrl: String): String {
        return buildString {
            appendLine("### gRPC ${route.target}")
            appendLine("GRPC $baseUrl/${route.path.trim('/')}")
            appendLine()
            appendLine("# Invoke via DocService: ${baseUrl}/docs")
            appendLine()
        }
    }

    fun pathWithPlaceholders(path: String): String {
        var resolved = BRACE_PATH_VARIABLE.replace(path) { match -> sampleValue(match.groupValues[1]) }
        resolved = COLON_PATH_VARIABLE.replace(resolved) { match -> sampleValue(match.groupValues[1]) }
        return resolved
    }

    private fun sampleValue(name: String): String = when {
        name.equals("id", ignoreCase = true) -> "1"
        name.equals("name", ignoreCase = true) -> "example"
        name.all { it.isDigit() } -> "sample"
        else -> name.lowercase(Locale.ROOT)
    }

    private fun pathSlug(path: String): String {
        val trimmed = path.trim('/')
        if (trimmed.isEmpty()) {
            return "root"
        }
        return trimmed
            .replace('/', '-')
            .replace(NON_SLUG_CHARACTERS, "-")
    }
}
