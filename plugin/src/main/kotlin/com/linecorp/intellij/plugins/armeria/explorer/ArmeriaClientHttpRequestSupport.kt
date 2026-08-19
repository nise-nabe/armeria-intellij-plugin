package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientEndpoint
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientRouteLinkSupport
import com.linecorp.intellij.plugins.armeria.client.ClientProtocol
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaHttpRequestGenerator
import com.linecorp.intellij.plugins.armeria.message
import java.net.URI

internal object ArmeriaClientHttpRequestSupport {
    fun supports(endpoint: ArmeriaClientEndpoint): Boolean = endpoint.isCallSite

    fun toRoute(endpoint: ArmeriaClientEndpoint): ArmeriaRoute? {
        val element = endpoint.pointer.element ?: return null
        val path = endpoint.requestPath?.takeIf { it.isNotBlank() } ?: return null
        val method = endpoint.httpMethod.takeIf { it.isNotBlank() } ?: return null
        val contentHints =
            endpoint.contentType
                ?.takeIf { it.isNotBlank() }
                ?.let { listOf(message("route.explorer.hint.consumes", it)) }
                .orEmpty()
        return ArmeriaRoute.create(
            element = element,
            protocol = ClientProtocol.HTTP.presentableName(),
            httpMethod = method,
            path = path,
            target = endpoint.target,
            routeMatch = RouteMatch.ANNOTATED_HTTP,
            contentHints = contentHints,
            exampleRequests = listOfNotNull(endpoint.requestBody?.takeIf { it.isNotBlank() }),
            exampleHeaders = endpoint.requestHeaders,
        )
    }

    fun baseUrl(endpoint: ArmeriaClientEndpoint): String {
        val trimmed = endpoint.uri.trim()
        val schemeSeparator = trimmed.indexOf("://")
        if (schemeSeparator <= 0) {
            return ArmeriaHttpRequestGenerator.DEFAULT_BASE_URL
        }
        val scheme = trimmed.substring(0, schemeSeparator)
        if (!ArmeriaClientRouteLinkSupport.isHttpLikeScheme(scheme)) {
            return ArmeriaHttpRequestGenerator.DEFAULT_BASE_URL
        }
        return try {
            val uri = URI(trimmed)
            val host = uri.host ?: return ArmeriaHttpRequestGenerator.DEFAULT_BASE_URL
            buildString {
                append(uri.scheme ?: "http")
                append("://")
                append(host)
                if (uri.port >= 0) {
                    append(':')
                    append(uri.port)
                }
            }
        } catch (_: Exception) {
            ArmeriaHttpRequestGenerator.DEFAULT_BASE_URL
        }
    }
}
