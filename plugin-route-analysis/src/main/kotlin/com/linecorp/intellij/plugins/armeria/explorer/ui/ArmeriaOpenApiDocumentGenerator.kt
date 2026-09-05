package com.linecorp.intellij.plugins.armeria.explorer.ui

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.GrpcRoutePath
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaGrpcServiceOptionsSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaPathVariableSupport
import com.linecorp.intellij.plugins.armeria.message
import java.util.Locale

object ArmeriaOpenApiDocumentGenerator {
    const val FILE_NAME = "armeria-openapi.yaml"
    const val OPENAPI_VERSION = "3.0.3"
    const val INFO_VERSION = "0.0.1"

    private val OPENAPI_METHODS =
        listOf("get", "put", "post", "delete", "options", "head", "patch", "trace")
    private val OPENAPI_METHOD_SET = OPENAPI_METHODS.toSet()
    private val SIMPLE_IDENTIFIER = Regex("^[A-Za-z_][A-Za-z0-9_]*$")
    private val STATUS_CODE = Regex("""^\d{3}$""")
    private val OPERATION_ID_INVALID = Regex("[^A-Za-z0-9_.-]+")

    fun exportable(route: ArmeriaRoute): Boolean {
        if (isUnframedGrpcMethod(route)) {
            return true
        }
        if (route.routeMatch != RouteMatch.ANNOTATED_HTTP) {
            return false
        }
        if (route.pathType != PathType.EXACT) {
            return false
        }
        return openApiMethod(route.httpMethod) != null
    }

    fun document(routes: List<ArmeriaRoute>): String {
        val operationsByPath = linkedMapOf<String, LinkedHashMap<String, OpenApiOperation>>()
        val duplicates = mutableListOf<Pair<String, String>>()
        val usedOperationIds = mutableSetOf<String>()
        for (route in routes) {
            if (!exportable(route)) {
                continue
            }
            val path = toOpenApiPath(route.path)
            val method = httpMethod(route)
            val methods = operationsByPath.getOrPut(path) { linkedMapOf() }
            if (method in methods) {
                duplicates += method.uppercase(Locale.ROOT) to path
                continue
            }
            methods[method] = toOperation(route, path, method, usedOperationIds)
        }
        return buildString {
            writeComments(routes, duplicates)
            appendKeyValue(0, "openapi", OPENAPI_VERSION)
            appendMap(0, "info") {
                appendKeyValue(1, "title", message("route.explorer.openapi.info.title"))
                appendKeyValue(1, "description", message("route.explorer.openapi.info.description"))
                appendKeyValue(1, "version", INFO_VERSION)
            }
            appendLine("servers:")
            appendDashField(1, "url", ArmeriaHttpRequestGenerator.DEFAULT_BASE_URL)
            if (operationsByPath.isEmpty()) {
                appendLine("paths: {}")
                return@buildString
            }
            appendMap(0, "paths") {
                for ((path, methods) in operationsByPath) {
                    appendMap(1, path) {
                        for (method in OPENAPI_METHODS) {
                            val operation = methods[method] ?: continue
                            writeOperation(2, method, operation)
                        }
                    }
                }
            }
        }
    }

    private fun StringBuilder.writeComments(
        routes: List<ArmeriaRoute>,
        duplicates: List<Pair<String, String>>,
    ) {
        val omitted = routes.filterNot(::exportable)
        if (omitted.isNotEmpty()) {
            val counts =
                omitted
                    .groupingBy { it.protocol.ifBlank { it.routeMatch.name } }
                    .eachCount()
                    .toSortedMap()
            val items =
                counts.entries.joinToString(", ") { (protocol, count) ->
                    message("route.explorer.openapi.comment.omittedItem", protocol, count)
                }
            appendComment(message("route.explorer.openapi.comment.omitted", omitted.size, items))
        }
        for ((method, path) in duplicates) {
            appendComment(message("route.explorer.openapi.comment.duplicate", method, path))
        }
    }

    private fun StringBuilder.writeOperation(
        indent: Int,
        method: String,
        operation: OpenApiOperation,
    ) {
        appendMap(indent, method) {
            appendKeyValue(indent + 1, "operationId", operation.operationId)
            operation.summary?.let { appendKeyValue(indent + 1, "summary", it) }
            if (operation.parameters.isNotEmpty()) {
                appendLine("${indent(indent + 1)}parameters:")
                for (parameter in operation.parameters) {
                    writeParameter(indent + 2, parameter)
                }
            }
            if (operation.requestContent.isNotEmpty()) {
                appendMap(indent + 1, "requestBody") {
                    appendKeyValue(indent + 2, "required", true)
                    writeContent(indent + 2, operation.requestContent)
                }
            }
            appendMap(indent + 1, "responses") {
                appendMap(indent + 2, operation.statusCode) {
                    appendKeyValue(
                        indent + 3,
                        "description",
                        message("route.explorer.openapi.response.description", operation.statusCode),
                    )
                    if (operation.responseContent.isNotEmpty()) {
                        writeContent(indent + 3, operation.responseContent)
                    }
                }
            }
        }
    }

    private fun StringBuilder.writeParameter(
        indent: Int,
        parameter: OpenApiParameter,
    ) {
        appendDashField(indent, "name", parameter.name)
        appendKeyValue(indent + 1, "in", parameter.location)
        appendKeyValue(indent + 1, "required", parameter.required)
        appendMap(indent + 1, "schema") {
            appendKeyValue(indent + 2, "type", "string")
        }
    }

    private fun StringBuilder.writeContent(
        indent: Int,
        mediaTypes: List<String>,
    ) {
        appendMap(indent, "content") {
            for (mediaType in mediaTypes) {
                appendMap(indent + 1, mediaType) {
                    appendMap(indent + 2, "schema") {
                        if (ArmeriaRouteContentHintSupport.isJsonMediaType(mediaType)) {
                            appendKeyValue(indent + 3, "type", "object")
                        } else {
                            appendKeyValue(indent + 3, "type", "string")
                            if (isBinaryMediaType(mediaType)) {
                                appendKeyValue(indent + 3, "format", "binary")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun toOperation(
        route: ArmeriaRoute,
        path: String,
        method: String,
        usedOperationIds: MutableSet<String>,
    ): OpenApiOperation {
        val unframedGrpc = isUnframedGrpcMethod(route)
        val consumes =
            if (unframedGrpc) {
                listOf(ArmeriaHttpRequestGenerator.JSON_MEDIA_TYPE)
            } else {
                ArmeriaRouteContentHintSupport.mediaTypes(route.contentHints, "route.explorer.hint.consumes")
            }
        val produces =
            if (unframedGrpc) {
                listOf(ArmeriaHttpRequestGenerator.JSON_MEDIA_TYPE)
            } else {
                ArmeriaRouteContentHintSupport.mediaTypes(route.contentHints, "route.explorer.hint.produces")
            }
        val pathParameters =
            ArmeriaPathVariableSupport
                .extractPathVariables(route.path, route.pathType)
                .ifEmpty { ArmeriaPathVariableSupport.extractPathVariables(path) }
                .distinct()
                .map { OpenApiParameter(it, "path", required = true) }
        val headerParameters =
            ArmeriaRouteContentHintSupport
                .equalityMatchFields(route.contentHints, "route.explorer.hint.matchesHeader")
                .map { (name, _) -> OpenApiParameter(name, "header", required = true) }
        val queryParameters =
            ArmeriaRouteContentHintSupport
                .equalityMatchFields(route.contentHints, "route.explorer.hint.matchesParam")
                .map { (name, _) -> OpenApiParameter(name, "query", required = true) }
        val requestContent =
            if (ArmeriaRouteContentHintSupport.hasRequestBody(method)) consumes.distinct() else emptyList()
        return OpenApiOperation(
            operationId = uniqueOperationId(route, usedOperationIds),
            summary = firstDescription(route.contentHints),
            parameters = (pathParameters + headerParameters + queryParameters).distinctBy { it.name to it.location },
            requestContent = requestContent,
            responseContent = produces.distinct(),
            statusCode = statusCode(route.contentHints),
        )
    }

    private fun uniqueOperationId(
        route: ArmeriaRoute,
        usedOperationIds: MutableSet<String>,
    ): String {
        val base =
            route.target
                .replace(OPERATION_ID_INVALID, "_")
                .trim('_')
                .ifEmpty { "operation" }
        var candidate = base
        var index = 2
        while (!usedOperationIds.add(candidate)) {
            candidate = "${base}_$index"
            index++
        }
        return candidate
    }

    private fun firstDescription(contentHints: List<String>): String? =
        ArmeriaRouteContentHintSupport
            .payloads(contentHints, "route.explorer.hint.description")
            .map(ArmeriaRouteContentHintSupport::collapseNewlines)
            .firstOrNull { it.isNotEmpty() }

    private fun statusCode(contentHints: List<String>): String {
        val payload =
            ArmeriaRouteContentHintSupport
                .payloads(contentHints, "route.explorer.hint.statusCode")
                .firstOrNull()
                ?.trim()
        return if (payload != null && STATUS_CODE.matches(payload)) payload else "200"
    }

    private fun httpMethod(route: ArmeriaRoute): String =
        if (isUnframedGrpcMethod(route)) {
            "post"
        } else {
            openApiMethod(route.httpMethod) ?: error("Unsupported HTTP method: ${route.httpMethod}")
        }

    private fun openApiMethod(method: String): String? {
        val normalized = method.lowercase(Locale.ROOT)
        return normalized.takeIf { it in OPENAPI_METHOD_SET }
    }

    private fun isUnframedGrpcMethod(route: ArmeriaRoute): Boolean {
        if (route.routeMatch != RouteMatch.NON_HTTP) {
            return false
        }
        if (!route.protocol.equals(RouteProtocol.GRPC.presentableName(), ignoreCase = true)) {
            return false
        }
        if (!GrpcRoutePath.isMethodPath(route.path)) {
            return false
        }
        return ArmeriaGrpcServiceOptionsSupport.hasUnframedHint(route.contentHints)
    }

    private fun toOpenApiPath(rawPath: String): String {
        val converted = ArmeriaPathVariableSupport.pathWithPlaceholders(rawPath.trim(), PathType.EXACT)
        return ensureLeadingSlash(converted)
    }

    private fun ensureLeadingSlash(path: String): String {
        if (path.isEmpty()) {
            return "/"
        }
        return if (path.startsWith("/")) path else "/$path"
    }

    private fun isBinaryMediaType(mediaType: String): Boolean {
        val normalized = mediaType.substringBefore(';').trim().lowercase(Locale.ROOT)
        return normalized == "application/binary" ||
            normalized == "application/octet-stream" ||
            normalized.endsWith("+binary")
    }

    private fun StringBuilder.appendComment(text: String) {
        text.lineSequence().forEach { line ->
            append("# ").append(line).append('\n')
        }
    }

    private fun StringBuilder.appendMap(
        indent: Int,
        key: String,
        block: StringBuilder.() -> Unit,
    ) {
        append(indent(indent))
            .append(yamlKey(key))
            .append(":\n")
        block()
    }

    private fun StringBuilder.appendKeyValue(
        indent: Int,
        key: String,
        value: String,
    ) {
        append(indent(indent))
            .append(yamlKey(key))
            .append(": ")
            .append(yamlScalar(value))
            .append('\n')
    }

    private fun StringBuilder.appendKeyValue(
        indent: Int,
        key: String,
        value: Boolean,
    ) {
        append(indent(indent))
            .append(yamlKey(key))
            .append(": ")
            .append(value)
            .append('\n')
    }

    private fun StringBuilder.appendDashField(
        indent: Int,
        key: String,
        value: String,
    ) {
        append(indent(indent))
            .append("- ")
            .append(yamlKey(key))
            .append(": ")
            .append(yamlScalar(value))
            .append('\n')
    }

    private fun indent(level: Int): String = "  ".repeat(level)

    private fun yamlKey(value: String): String =
        if (SIMPLE_IDENTIFIER.matches(value)) {
            value
        } else {
            yamlScalar(value)
        }

    private fun yamlScalar(value: String): String =
        buildString {
            append('"')
            for (ch in value) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
            append('"')
        }

    private data class OpenApiOperation(
        val operationId: String,
        val summary: String?,
        val parameters: List<OpenApiParameter>,
        val requestContent: List<String>,
        val responseContent: List<String>,
        val statusCode: String,
    )

    private data class OpenApiParameter(
        val name: String,
        val location: String,
        val required: Boolean,
    )
}
