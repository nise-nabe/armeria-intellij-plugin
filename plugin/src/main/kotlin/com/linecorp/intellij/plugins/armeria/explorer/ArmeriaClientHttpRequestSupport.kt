package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientEndpoint
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientRouteLinkSupport
import com.linecorp.intellij.plugins.armeria.client.ClientProtocol
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaHttpRequestGenerator
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaClientHttpRequestSupport {
    fun supports(endpoint: ArmeriaClientEndpoint): Boolean = endpoint.isCallSite

    fun toRoute(endpoint: ArmeriaClientEndpoint): ArmeriaRoute? {
        val element = endpoint.pointer.element ?: return null
        val rawPath = endpoint.requestPath?.takeIf { it.isNotBlank() } ?: return null
        val path = ArmeriaClientRouteLinkSupport.pathForMatching(rawPath)
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
        val requestPath = endpoint.requestPath
        if (!requestPath.isNullOrBlank() && ArmeriaClientRouteLinkSupport.isAbsoluteHttpUri(requestPath)) {
            ArmeriaClientRouteLinkSupport.httpOrigin(requestPath)?.let { return it }
        }
        return ArmeriaClientRouteLinkSupport.httpOrigin(endpoint.uri)
            ?: ArmeriaHttpRequestGenerator.DEFAULT_BASE_URL
    }
}
