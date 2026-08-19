package com.linecorp.intellij.plugins.armeria.client

internal object ArmeriaClientInvocationSupport {
    const val REST_CLIENT_PREPARATION_CLASS = "com.linecorp.armeria.client.RestClientPreparation"

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

    fun displayPath(endpoint: ArmeriaClientEndpoint): String = endpoint.requestPath ?: endpoint.uri
}
