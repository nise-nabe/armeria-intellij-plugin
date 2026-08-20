package com.linecorp.intellij.plugins.armeria.client

import java.util.Locale

internal object ArmeriaClientInvocationSupport {
    const val REST_CLIENT_PREPARATION_CLASS = "com.linecorp.armeria.client.RestClientPreparation"

    private val HTTP_CLIENT_SIMPLE_NAMES = setOf("RestClient", "WebClient", "BlockingWebClient")

    private val JAVA_IDENTIFIER = Regex("[A-Za-z_][A-Za-z0-9_]*")

    private val BODY_HTTP_METHODS = setOf("POST", "PUT", "PATCH")

    private val MEDIA_TYPES_BY_CONSTANT =
        mapOf(
            "JSON" to "application/json",
            "JSON_UTF_8" to "application/json",
            "PLAIN_TEXT" to "text/plain",
            "PLAIN_TEXT_UTF_8" to "text/plain",
            "HTML_UTF_8" to "text/html",
            "FORM_DATA" to "application/x-www-form-urlencoded",
            "EVENT_STREAM" to "text/event-stream",
            "PROTOBUF" to "application/protobuf",
            "OCTET_STREAM" to "application/octet-stream",
        )

    private val HEADER_NAMES_BY_CONSTANT =
        mapOf(
            "CONTENT_TYPE" to "Content-Type",
            "ACCEPT" to "Accept",
            "AUTHORIZATION" to "Authorization",
            "USER_AGENT" to "User-Agent",
            "COOKIE" to "Cookie",
        )

    fun isPreparationClass(qualifiedName: String?): Boolean =
        qualifiedName == REST_CLIENT_PREPARATION_CLASS ||
            qualifiedName?.endsWith(".RestClientPreparation") == true

    fun mediaTypeFromConstantName(name: String?): String? = name?.let { MEDIA_TYPES_BY_CONSTANT[it] }

    fun headerNameFromConstantName(name: String?): String? = name?.let { HEADER_NAMES_BY_CONSTANT[it] }

    fun isResolvedPath(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty() || '(' in trimmed || '\n' in trimmed) {
            return false
        }
        return true
    }

    fun capturesRequestBody(httpMethod: String): Boolean = httpMethod.uppercase(Locale.ROOT) in BODY_HTTP_METHODS

    fun containsHttpClientSimpleName(text: String): Boolean = JAVA_IDENTIFIER.findAll(text).any { it.value in HTTP_CLIENT_SIMPLE_NAMES }

    fun displayPath(endpoint: ArmeriaClientEndpoint): String {
        val requestPath = endpoint.requestPath
        if (requestPath.isNullOrBlank()) {
            return endpoint.uri
        }
        return ArmeriaClientRouteLinkSupport.pathForMatching(requestPath)
    }

    fun presentableLabel(endpoint: ArmeriaClientEndpoint): String {
        if (!endpoint.isCallSite) {
            return "${endpoint.clientType} ${endpoint.uri}"
        }
        val path = displayPath(endpoint)
        val origin = originLabel(endpoint)
        return buildString {
            append(endpoint.clientType)
            append(' ')
            append(endpoint.httpMethod)
            append(' ')
            append(path)
            if (!origin.isNullOrBlank() && origin != path) {
                append(' ')
                append(origin)
            }
        }
    }

    fun chooserLabel(endpoint: ArmeriaClientEndpoint): String = "${presentableLabel(endpoint)} (${endpoint.moduleName})"

    private fun originLabel(endpoint: ArmeriaClientEndpoint): String? {
        val uri = endpoint.uri.trim()
        val path = displayPath(endpoint)
        if (uri.isNotBlank() && uri != path && uri != endpoint.requestPath) {
            return uri
        }
        val requestPath = endpoint.requestPath ?: return null
        return ArmeriaClientRouteLinkSupport.httpOrigin(requestPath)
    }
}
