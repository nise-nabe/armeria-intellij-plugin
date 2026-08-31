package com.linecorp.intellij.plugins.armeria.explorer.ui

import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceDebugFormUrl
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceMethodRef
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.GrpcRoutePath
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaGrpcServiceOptionsSupport
import com.linecorp.intellij.plugins.armeria.message
import java.util.Locale

object ArmeriaHttpRequestGenerator {
    const val DEFAULT_BASE_URL = "http://localhost:8080"
    const val JSON_MEDIA_TYPE = "application/json"

    private val NON_SLUG_CHARACTERS = Regex("[^a-zA-Z0-9._-]")
    private val NEWLINE_CHARACTERS = Regex("[\\r\\n]+")
    private val COLON_PATH_VARIABLE = Regex(""":([A-Za-z_][A-Za-z0-9_]*)""")
    private val GRAPHQL_OPERATION_TARGET = Regex("""^(Query|Mutation|Subscription)\.[A-Za-z_][A-Za-z0-9_]*$""")
    // tchar minus a trailing '!' immediately before '=' so `name!=value` is not a header pair.

    private val SIMPLE_HEADER_MATCH = Regex("""^([A-Za-z0-9_!#\$%&'*+.^`|~-]+)(?<!!)=([^=].*)$""")
    private val METHODS_WITH_BODY = setOf("POST", "PUT", "PATCH")

    fun supports(route: ArmeriaRoute): Boolean {
        if (isWebSocketRoute(route)) {
            return false
        }
        return when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP -> route.httpMethod.isNotBlank()
            RouteMatch.DELEGATED -> true
            RouteMatch.SERVICE, RouteMatch.SERVICE_UNDER, RouteMatch.HEALTH_CHECK, RouteMatch.ROUTE_FLUENT -> true
            RouteMatch.RUNTIME, RouteMatch.CONFIG -> route.httpMethod.isNotBlank()
            RouteMatch.NON_HTTP -> isGrpcRoute(route) || isGraphqlRoute(route)
            RouteMatch.ANNOTATED_SERVICE, RouteMatch.FILE_SERVICE, RouteMatch.VIRTUAL_HOST,
            RouteMatch.ROUTE_DECORATOR, RouteMatch.DECORATOR_UNDER,
            -> false
        }
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
            return if (isUnframedGrpc(route)) {
                unframedGrpcRequestText(route, normalizedBaseUrl)
            } else {
                grpcRequestText(route, normalizedBaseUrl)
            }
        }
        val method = httpMethod(route)
        val resolvedPath = pathWithPlaceholders(route.path, route.pathType)
        val pathWithQuery = appendQuery(resolvedPath, matchQueryString(route.contentHints))
        val consumes = ArmeriaRouteContentHintSupport.mediaTypes(route.contentHints, "route.explorer.hint.consumes")
        val produces = ArmeriaRouteContentHintSupport.mediaTypes(route.contentHints, "route.explorer.hint.produces")
        val accept = produces.firstOrNull() ?: JSON_MEDIA_TYPE
        val contentType = contentTypeForMethod(method, consumes)
        val exampleBody =
            route.exampleRequests
                .firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        return buildString {
            appendLine("### ${route.path}")
            for (comment in descriptionComments(route.contentHints)) {
                appendLine("# $comment")
            }
            if (exampleBody != null && method.uppercase(Locale.ROOT) !in METHODS_WITH_BODY) {
                appendLine("# Example request: ${exampleBody.replace(NEWLINE_CHARACTERS, " ").trim()}")
            }
            appendLine("$method $normalizedBaseUrl$pathWithQuery")
            val emittedHeaderNames = linkedSetOf<String>()
            for ((name, value) in matchHeaderFields(route.contentHints)) {
                appendLine("$name: $value")
                emittedHeaderNames += name.lowercase(Locale.ROOT)
            }
            for (header in route.exampleHeaders) {
                val headerName = header.substringBefore(':').trim()
                if (headerName.isEmpty() || headerName.lowercase(Locale.ROOT) in emittedHeaderNames) {
                    continue
                }
                appendLine(header)
                emittedHeaderNames += headerName.lowercase(Locale.ROOT)
            }
            val resolvedContentType =
                contentType
                    ?: exampleBody?.takeIf { method.uppercase(Locale.ROOT) in METHODS_WITH_BODY }?.let { JSON_MEDIA_TYPE }
            if (resolvedContentType != null && "content-type" !in emittedHeaderNames) {
                appendLine("Content-Type: $resolvedContentType")
            }
            if ("accept" !in emittedHeaderNames) {
                appendLine("Accept: $accept")
            }
            appendLine()
            if (method.uppercase(Locale.ROOT) in METHODS_WITH_BODY) {
                when {
                    resolvedContentType != null && isJsonMediaType(resolvedContentType) ->
                        appendLine(exampleBody ?: "{}")
                    exampleBody != null -> appendLine(exampleBody)
                }
            }
        }
    }

    private fun isWebSocketRoute(route: ArmeriaRoute): Boolean =
        route.protocol.equals(RouteProtocol.WEBSOCKET.presentableName(), ignoreCase = true)

    private fun isGrpcRoute(route: ArmeriaRoute): Boolean {
        if (route.routeMatch != RouteMatch.NON_HTTP) {
            return false
        }
        if (!route.protocol.equals(RouteProtocol.GRPC.presentableName(), ignoreCase = true)) {
            return false
        }
        return GrpcRoutePath.isMethodPath(route.path)
    }

    private fun isUnframedGrpc(route: ArmeriaRoute): Boolean = ArmeriaGrpcServiceOptionsSupport.hasUnframedHint(route.contentHints)

    private fun isGraphqlRoute(route: ArmeriaRoute): Boolean {
        if (route.routeMatch != RouteMatch.NON_HTTP) {
            return false
        }
        if (!route.protocol.equals(RouteProtocol.GRAPHQL.presentableName(), ignoreCase = true)) {
            return false
        }
        return GRAPHQL_OPERATION_TARGET.matches(route.target)
    }

    private fun grpcRequestText(
        route: ArmeriaRoute,
        baseUrl: String,
    ): String {
        val grpcPath = route.path.trim('/')
        val exampleBody =
            route.exampleRequests
                .firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "{}"
        val debugFormUrl =
            ArmeriaDocServiceMethodRef.from(route)?.let { ref ->
                ArmeriaDocServiceDebugFormUrl.build("$baseUrl/docs", ref)
            } ?: "$baseUrl/docs"
        return buildString {
            appendLine("### gRPC ${route.target}")
            appendLine("GRPC $baseUrl/$grpcPath")
            appendLine()
            appendLine("# Invoke via DocService: $debugFormUrl")
            for (header in route.exampleHeaders) {
                appendLine("# $header")
            }
            appendLine("# gRPC-JSON uses POST with a JSON body:")
            appendLine(exampleBody)
            appendLine()
        }
    }

    private fun unframedGrpcRequestText(
        route: ArmeriaRoute,
        baseUrl: String,
    ): String {
        val grpcPath = "/" + route.path.trim('/')
        val exampleBody =
            route.exampleRequests
                .firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "{}"
        val debugFormUrl =
            ArmeriaDocServiceMethodRef.from(route)?.let { ref ->
                ArmeriaDocServiceDebugFormUrl.build("$baseUrl/docs", ref)
            } ?: "$baseUrl/docs"
        return buildString {
            appendLine("### gRPC ${route.target}")
            appendLine("POST $baseUrl$grpcPath")
            appendLine("Content-Type: $JSON_MEDIA_TYPE")
            appendLine("Accept: $JSON_MEDIA_TYPE")
            appendLine()
            appendLine("# Invoke via DocService: $debugFormUrl")
            for (header in route.exampleHeaders) {
                appendLine("# $header")
            }
            appendLine("# ${message("route.explorer.http.grpcProtobufAlternate")}")
            appendLine(exampleBody)
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
        equalityMatchFields(contentHints, "route.explorer.hint.matchesHeader")

    private fun matchQueryString(contentHints: List<String>): String {
        val fields = equalityMatchFields(contentHints, "route.explorer.hint.matchesParam")
        if (fields.isEmpty()) {
            return ""
        }
        return fields.joinToString("&") { (name, value) -> "$name=$value" }
    }

    private fun appendQuery(
        path: String,
        query: String,
    ): String {
        if (query.isEmpty()) {
            return path
        }
        val separator = if (path.contains('?')) '&' else '?'
        return "$path$separator$query"
    }

    private fun descriptionComments(contentHints: List<String>): List<String> =
        ArmeriaRouteContentHintSupport
            .payloads(contentHints, "route.explorer.hint.description")
            .map { text -> text.replace(NEWLINE_CHARACTERS, " ").trim() }
            .filter { it.isNotEmpty() }

    private fun equalityMatchFields(
        contentHints: List<String>,
        messageKey: String,
    ): List<Pair<String, String>> =
        ArmeriaRouteContentHintSupport
            .payloads(contentHints, messageKey)
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
