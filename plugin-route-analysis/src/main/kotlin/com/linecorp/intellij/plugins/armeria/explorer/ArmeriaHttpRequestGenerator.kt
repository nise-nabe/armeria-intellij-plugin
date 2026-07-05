package com.linecorp.intellij.plugins.armeria.explorer

import java.util.Locale

object ArmeriaHttpRequestGenerator {
    const val DEFAULT_BASE_URL = "http://localhost:8080"

    private val NON_SLUG_CHARACTERS = Regex("[^a-zA-Z0-9._-]")
    private val COLON_PATH_VARIABLE = Regex(""":([A-Za-z_][A-Za-z0-9_]*)""")
    private val GRPC_METHOD_PATH = Regex("""^/[^/]+/[^/]+$""")

    fun supports(route: ArmeriaRoute): Boolean {
        return when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP -> route.httpMethod.isNotBlank()
            RouteMatch.DELEGATED_SPRING_MVC, RouteMatch.DELEGATED_SERVLET -> true
            RouteMatch.SERVICE, RouteMatch.SERVICE_UNDER, RouteMatch.HEALTH_CHECK, RouteMatch.ROUTE_FLUENT -> true
            RouteMatch.RUNTIME, RouteMatch.CONFIG -> route.httpMethod.isNotBlank()
            RouteMatch.NON_HTTP -> isGrpcRoute(route)
            RouteMatch.ANNOTATED_SERVICE, RouteMatch.FILE_SERVICE, RouteMatch.VIRTUAL_HOST,
            RouteMatch.ROUTE_DECORATOR, RouteMatch.DECORATOR_UNDER,
            -> false
        }
    }

    fun httpMethod(route: ArmeriaRoute): String {
        return when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP, RouteMatch.RUNTIME, RouteMatch.CONFIG -> route.httpMethod
            RouteMatch.DELEGATED_SPRING_MVC, RouteMatch.DELEGATED_SERVLET,
            RouteMatch.SERVICE, RouteMatch.SERVICE_UNDER, RouteMatch.HEALTH_CHECK, RouteMatch.ROUTE_FLUENT,
            -> route.httpMethod.ifBlank { "GET" }
            RouteMatch.NON_HTTP -> {
                if (isGrpcRoute(route)) {
                    "POST"
                } else {
                    error("Unsupported route match: ${route.routeMatch}")
                }
            }
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
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        if (isGrpcRoute(route)) {
            return grpcRequestText(route, normalizedBaseUrl)
        }
        val method = httpMethod(route)
        val resolvedPath = pathWithPlaceholders(route.path, route.pathType)
        return buildString {
            appendLine("### ${route.path}")
            appendLine("$method $normalizedBaseUrl$resolvedPath")
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
        val grpcPath = route.path.trim('/')
        return buildString {
            appendLine("### gRPC ${route.target}")
            appendLine("GRPC $baseUrl/$grpcPath")
            appendLine()
            appendLine("# Invoke via DocService: $baseUrl/docs")
            appendLine()
        }
    }

    private fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trimEnd('/')

    private fun pathWithPlaceholders(path: String, pathType: PathType): String {
        if (pathType == PathType.REGEX || pathType == PathType.GLOB) {
            return path
        }
        var resolved = replaceBracePathVariables(path)
        resolved = COLON_PATH_VARIABLE.replace(resolved) { match -> sampleValue(match.groupValues[1]) }
        return resolved
    }

    private fun replaceBracePathVariables(path: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < path.length) {
            if (path[index] == '{') {
                val end = findMatchingBrace(path, index)
                if (end < 0) {
                    result.append(path[index])
                    index++
                    continue
                }
                val capture = path.substring(index + 1, end)
                result.append(sampleValue(braceVariableName(capture)))
                index = end + 1
            } else {
                result.append(path[index])
                index++
            }
        }
        return result.toString()
    }

    private fun findMatchingBrace(path: String, start: Int): Int {
        if (start >= path.length || path[start] != '{') {
            return -1
        }
        var depth = 0
        for (index in start until path.length) {
            when (path[index]) {
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        return index
                    }
                }
            }
        }
        return -1
    }

    private fun braceVariableName(capture: String): String {
        val trimmed = capture.trim()
        val colonIndex = trimmed.indexOf(':')
        return if (colonIndex < 0) trimmed else trimmed.substring(0, colonIndex).trim()
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
