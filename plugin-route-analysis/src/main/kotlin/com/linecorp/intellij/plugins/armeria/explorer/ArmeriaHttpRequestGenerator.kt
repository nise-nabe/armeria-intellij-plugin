package com.linecorp.intellij.plugins.armeria.explorer

import java.util.Locale

object ArmeriaHttpRequestGenerator {
    const val DEFAULT_BASE_URL = "http://localhost:8080"

    private val NON_SLUG_CHARACTERS = Regex("[^a-zA-Z0-9._-]")
    private val BRACE_PATH_VARIABLE = Regex("""\{([^}]+)}""")
    private val COLON_PATH_VARIABLE = Regex(""":([A-Za-z_][A-Za-z0-9_]*)""")
    private val GRPC_METHOD_PATH = Regex("""^/[^/]+\.[^/]+/[^/]+$""")

    fun supports(route: ArmeriaRoute): Boolean {
        return when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP -> route.httpMethod.isNotBlank()
            RouteMatch.SERVICE, RouteMatch.SERVICE_UNDER, RouteMatch.HEALTH_CHECK, RouteMatch.ROUTE_FLUENT -> true
            RouteMatch.RUNTIME -> route.httpMethod.isNotBlank()
            RouteMatch.NON_HTTP -> isGrpcRoute(route)
            RouteMatch.ANNOTATED_SERVICE, RouteMatch.FILE_SERVICE, RouteMatch.VIRTUAL_HOST,
            RouteMatch.ROUTE_DECORATOR, RouteMatch.DECORATOR_UNDER,
            -> false
        }
    }

    fun httpMethod(route: ArmeriaRoute): String {
        return when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP, RouteMatch.RUNTIME -> route.httpMethod
            RouteMatch.SERVICE, RouteMatch.SERVICE_UNDER, RouteMatch.HEALTH_CHECK, RouteMatch.ROUTE_FLUENT ->
                route.httpMethod.ifBlank { "GET" }
            RouteMatch.NON_HTTP -> "POST"
            RouteMatch.ANNOTATED_SERVICE, RouteMatch.FILE_SERVICE, RouteMatch.VIRTUAL_HOST,
            RouteMatch.ROUTE_DECORATOR, RouteMatch.DECORATOR_UNDER,
            -> error("Unsupported route match: ${route.routeMatch}")
        }
    }

    fun fileName(route: ArmeriaRoute): String {
        if (isGrpcRoute(route)) {
            val slug = pathSlug(route.path)
            return "armeria-grpc-$slug.http"
        }
        val method = httpMethod(route).lowercase(Locale.ROOT)
        return "armeria-$method-${pathSlug(route.path)}.http"
    }

    fun requestText(route: ArmeriaRoute, baseUrl: String = DEFAULT_BASE_URL): String {
        if (isGrpcRoute(route)) {
            return grpcRequestText(route, baseUrl)
        }
        val method = httpMethod(route)
        val resolvedPath = pathWithPlaceholders(route.path, route.pathType)
        return buildString {
            appendLine("### ${route.path}")
            appendLine("$method $baseUrl$resolvedPath")
            appendLine("Accept: application/json")
            appendLine()
        }
    }

    private fun isGrpcRoute(route: ArmeriaRoute): Boolean {
        if (route.routeMatch != RouteMatch.NON_HTTP) {
            return false
        }
        if (!route.protocol.equals(RouteProtocol.GRPC.presentableName(), ignoreCase = true)) {
            return false
        }
        return GRPC_METHOD_PATH.matches(route.path)
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

    private fun pathWithPlaceholders(path: String, pathType: PathType): String {
        if (pathType == PathType.REGEX || pathType == PathType.GLOB) {
            return path
        }
        var resolved = BRACE_PATH_VARIABLE.replace(path) { match ->
            sampleValue(braceVariableName(match.groupValues[1]))
        }
        resolved = COLON_PATH_VARIABLE.replace(resolved) { match -> sampleValue(match.groupValues[1]) }
        return resolved
    }

    private fun braceVariableName(capture: String): String {
        val colonIndex = capture.indexOf(':')
        return if (colonIndex < 0) capture else capture.substring(0, colonIndex)
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
