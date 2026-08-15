package com.linecorp.intellij.plugins.armeria.explorer.ui

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import java.util.Locale

object ArmeriaHttpRequestGenerator {
    const val DEFAULT_BASE_URL = "http://localhost:8080"
    const val JSON_MEDIA_TYPE = "application/json"

    private val NON_SLUG_CHARACTERS = Regex("[^a-zA-Z0-9._-]")
    private val COLON_PATH_VARIABLE = Regex(""":([A-Za-z_][A-Za-z0-9_]*)""")
    private val GRPC_METHOD_PATH = Regex("""^/[^/]+/[^/]+$""")
    private val SIMPLE_HEADER_MATCH = Regex("""^([A-Za-z0-9_-]+)=([^=].*)$""")
    private val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH")

    fun supports(route: ArmeriaRoute): Boolean =
        when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP -> route.httpMethod.isNotBlank()
            RouteMatch.DELEGATED -> true
            RouteMatch.SERVICE, RouteMatch.SERVICE_UNDER, RouteMatch.HEALTH_CHECK, RouteMatch.ROUTE_FLUENT -> true
            RouteMatch.RUNTIME, RouteMatch.CONFIG -> route.httpMethod.isNotBlank()
            RouteMatch.NON_HTTP -> isGrpcRoute(route) || isGraphqlRoute(route)
            RouteMatch.ANNOTATED_SERVICE, RouteMatch.FILE_SERVICE, RouteMatch.VIRTUAL_HOST,
            RouteMatch.ROUTE_DECORATOR, RouteMatch.DECORATOR_UNDER,
            -> false
        }

    fun httpMethod(route: ArmeriaRoute): String =
        when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP, RouteMatch.RUNTIME, RouteMatch.CONFIG -> route.httpMethod
            RouteMatch.DELEGATED,
            RouteMatch.SERVICE, RouteMatch.SERVICE_UNDER, RouteMatch.HEALTH_CHECK, RouteMatch.ROUTE_FLUENT,
            -> route.httpMethod.ifBlank { "GET" }
            RouteMatch.NON_HTTP -> {
                when {
                    isGrpcRoute(route) || isGraphqlRoute(route) -> "POST"
                    else -> error("Unsupported route match: ${route.routeMatch}")
                }
            }
            RouteMatch.ANNOTATED_SERVICE, RouteMatch.FILE_SERVICE, RouteMatch.VIRTUAL_HOST,
            RouteMatch.ROUTE_DECORATOR, RouteMatch.DECORATOR_UNDER,
            -> error("Unsupported route match: ${route.routeMatch}")
        }

    fun fileName(route: ArmeriaRoute): String {
        if (isGrpcRoute(route)) {
            val slug = pathSlug(route.path)
            return "armeria-grpc-$slug.http"
        }
        if (isGraphqlRoute(route)) {
            return "armeria-graphql-${pathSlug(route.target)}.http"
        }
        val method = httpMethod(route).lowercase(Locale.ROOT)
        return "armeria-$method-${pathSlug(route.path)}.http"
    }

    fun requestText(
        route: ArmeriaRoute,
        baseUrl: String = DEFAULT_BASE_URL,
    ): String {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        if (isGraphqlRoute(route)) {
            return graphqlRequestText(route, normalizedBaseUrl)
        }
        if (isGrpcRoute(route)) {
            return grpcRequestText(route, normalizedBaseUrl)
        }
        val method = httpMethod(route)
        val resolvedPath = pathWithPlaceholders(route.path, route.pathType)
        val consumes = ArmeriaRouteContentHintSupport.mediaTypes(route.contentHints, "route.explorer.hint.consumes")
        val produces = ArmeriaRouteContentHintSupport.mediaTypes(route.contentHints, "route.explorer.hint.produces")
        val accept = produces.firstOrNull() ?: JSON_MEDIA_TYPE
        val contentType = contentTypeForMethod(method, consumes)
        return buildString {
            appendLine("### ${route.path}")
            appendLine("$method $normalizedBaseUrl$resolvedPath")
            for ((name, value) in matchHeaderFields(route.contentHints)) {
                appendLine("$name: $value")
            }
            if (contentType != null) {
                appendLine("Content-Type: $contentType")
            }
            appendLine("Accept: $accept")
            appendLine()
            if (contentType != null && isJsonMediaType(contentType)) {
                appendLine("{}")
            }
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

    private fun isGraphqlRoute(route: ArmeriaRoute): Boolean {
        if (route.routeMatch != RouteMatch.NON_HTTP) {
            return false
        }
        return route.protocol.equals(RouteProtocol.GRAPHQL.presentableName(), ignoreCase = true)
    }

    private fun grpcRequestText(
        route: ArmeriaRoute,
        baseUrl: String,
    ): String {
        val grpcPath = route.path.trim('/')
        return buildString {
            appendLine("### gRPC ${route.target}")
            appendLine("GRPC $baseUrl/$grpcPath")
            appendLine()
            appendLine("# Invoke via DocService: $baseUrl/docs")
            appendLine("# gRPC-JSON uses POST with a JSON body:")
            appendLine("{}")
            appendLine()
        }
    }

    private fun graphqlRequestText(
        route: ArmeriaRoute,
        baseUrl: String,
    ): String {
        val path = route.path.ifBlank { "/graphql" }
        val query = graphqlOperationStub(route.target)
        return buildString {
            appendLine("### ${route.target}")
            appendLine("POST $baseUrl$path")
            appendLine("Content-Type: $JSON_MEDIA_TYPE")
            appendLine("Accept: $JSON_MEDIA_TYPE")
            appendLine()
            appendLine("""{"query": "$query"}""")
        }
    }

    private fun graphqlOperationStub(target: String): String {
        val trimmed = target.trim()
        val separator = trimmed.indexOf('.')
        val operationType: String
        val field: String
        if (separator < 0) {
            operationType = "query"
            field = trimmed
        } else {
            operationType = trimmed.substring(0, separator).lowercase(Locale.ROOT)
            field = trimmed.substring(separator + 1)
        }
        val keyword =
            when (operationType) {
                "mutation" -> "mutation"
                "subscription" -> "subscription"
                else -> "query"
            }
        val fieldName = graphqlFieldName(field)
        return "$keyword { $fieldName }"
    }

    private fun graphqlFieldName(field: String): String {
        val identifier = field.takeWhile { it.isLetterOrDigit() || it == '_' }
        return identifier.ifEmpty { "operation" }
    }

    private fun normalizeBaseUrl(baseUrl: String): String = baseUrl.trimEnd('/')

    private fun pathWithPlaceholders(
        path: String,
        pathType: PathType,
    ): String {
        if (pathType == PathType.REGEX || pathType == PathType.GLOB) {
            return path
        }
        var resolved = replaceBracePathVariables(path)
        resolved = COLON_PATH_VARIABLE.replace(resolved) { match -> "{${match.groupValues[1]}}" }
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
                result.append('{').append(braceVariableName(capture)).append('}')
                index = end + 1
            } else {
                result.append(path[index])
                index++
            }
        }
        return result.toString()
    }

    private fun findMatchingBrace(
        path: String,
        start: Int,
    ): Int {
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

    private fun contentTypeForMethod(
        method: String,
        consumes: List<String>,
    ): String? {
        if (method.uppercase(Locale.ROOT) !in METHODS_WITH_BODY || consumes.isEmpty()) {
            return null
        }
        return consumes.firstOrNull(::isJsonMediaType) ?: consumes.first()
    }

    private fun isJsonMediaType(mediaType: String): Boolean {
        val normalized = mediaType.substringBefore(';').trim().lowercase(Locale.ROOT)
        return normalized == JSON_MEDIA_TYPE || normalized.endsWith("+json")
    }

    private fun matchHeaderFields(contentHints: List<String>): List<Pair<String, String>> =
        ArmeriaRouteContentHintSupport
            .payloads(contentHints, "route.explorer.hint.matchesHeader")
            .mapNotNull { condition ->
                val match = SIMPLE_HEADER_MATCH.matchEntire(condition.trim()) ?: return@mapNotNull null
                match.groupValues[1] to match.groupValues[2]
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
