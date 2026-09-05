package com.linecorp.intellij.plugins.armeria.explorer.ui

import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceDebugFormUrl
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceMethodRef
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.GrpcRoutePath
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaGrpcServiceOptionsSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaPathVariableSupport
import com.linecorp.intellij.plugins.armeria.message
import java.util.Locale

object ArmeriaHttpRequestGenerator {
    const val DEFAULT_BASE_URL = "http://localhost:8080"
    const val JSON_MEDIA_TYPE = "application/json"

    private val NON_SLUG_CHARACTERS = Regex("[^a-zA-Z0-9._-]")
    private val GRAPHQL_OPERATION_TARGET = Regex("""^(Query|Mutation|Subscription)\.[A-Za-z_][A-Za-z0-9_]*$""")

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
            RouteMatch.LISTEN_PORT, RouteMatch.ROUTE_DECORATOR, RouteMatch.DECORATOR_UNDER,
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
            RouteMatch.LISTEN_PORT, RouteMatch.ROUTE_DECORATOR, RouteMatch.DECORATOR_UNDER,
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
        docsBaseUrl: String? = null,
    ): String {
        val normalizedBaseUrl = normalizeBaseUrl(baseUrl)
        val normalizedDocsBaseUrl = docsBaseUrl?.let(::normalizeBaseUrl)
        if (isGraphqlRoute(route)) {
            return graphqlRequestText(route, normalizedBaseUrl)
        }
        if (isGrpcRoute(route)) {
            return if (isUnframedGrpc(route)) {
                unframedGrpcRequestText(route, normalizedBaseUrl, normalizedDocsBaseUrl)
            } else {
                grpcRequestText(route, normalizedBaseUrl, normalizedDocsBaseUrl)
            }
        }
        val method = httpMethod(route)
        val resolvedPath = ArmeriaPathVariableSupport.pathWithPlaceholders(route.path, route.pathType)
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
            if (exampleBody != null && !ArmeriaRouteContentHintSupport.hasRequestBody(method)) {
                appendLine("# Example request: ${ArmeriaRouteContentHintSupport.collapseNewlines(exampleBody)}")
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
                    ?: exampleBody?.takeIf { ArmeriaRouteContentHintSupport.hasRequestBody(method) }?.let { JSON_MEDIA_TYPE }
            if (resolvedContentType != null && "content-type" !in emittedHeaderNames) {
                appendLine("Content-Type: $resolvedContentType")
            }
            if ("accept" !in emittedHeaderNames) {
                appendLine("Accept: $accept")
            }
            appendLine()
            if (ArmeriaRouteContentHintSupport.hasRequestBody(method)) {
                when {
                    resolvedContentType != null && ArmeriaRouteContentHintSupport.isJsonMediaType(resolvedContentType) ->
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
        docsBaseUrl: String?,
    ): String {
        val grpcPath = route.path.trim('/')
        val exampleBody =
            route.exampleRequests
                .firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "{}"
        val debugFormUrl = grpcDebugFormUrl(route, baseUrl, docsBaseUrl)
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
        docsBaseUrl: String?,
    ): String {
        val grpcPath = "/" + route.path.trim('/')
        val exampleBody =
            route.exampleRequests
                .firstOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "{}"
        val debugFormUrl = grpcDebugFormUrl(route, baseUrl, docsBaseUrl)
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

    private fun grpcDebugFormUrl(
        route: ArmeriaRoute,
        baseUrl: String,
        docsBaseUrl: String?,
    ): String {
        val docsBase = docsBaseUrl ?: "$baseUrl/docs"
        return ArmeriaDocServiceMethodRef.from(route)?.let { ref ->
            ArmeriaDocServiceDebugFormUrl.build(docsBase, ref)
        } ?: docsBase
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

    private fun contentTypeForMethod(
        method: String,
        consumes: List<String>,
    ): String? {
        if (!ArmeriaRouteContentHintSupport.hasRequestBody(method) || consumes.isEmpty()) {
            return null
        }
        return consumes.firstOrNull(ArmeriaRouteContentHintSupport::isJsonMediaType) ?: consumes.first()
    }

    private fun matchHeaderFields(contentHints: List<String>): List<Pair<String, String>> =
        ArmeriaRouteContentHintSupport.equalityMatchFields(contentHints, "route.explorer.hint.matchesHeader")

    private fun matchQueryString(contentHints: List<String>): String {
        val fields = ArmeriaRouteContentHintSupport.equalityMatchFields(contentHints, "route.explorer.hint.matchesParam")
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
            .map(ArmeriaRouteContentHintSupport::collapseNewlines)
            .filter { it.isNotEmpty() }

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
