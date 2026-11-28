package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceSupport
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaPathVariableSupport
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaHttpRequestGenerator
import java.net.URI

internal object ArmeriaRouteCurlSupport {
    fun curlText(
        route: ArmeriaRoute,
        staticRoutes: List<ArmeriaRoute>,
        lastSyncedDocsBaseUrl: String?,
    ): String? {
        if (route.routeMatch == RouteMatch.NON_HTTP || !ArmeriaHttpRequestGenerator.supports(route)) {
            return null
        }
        val method = ArmeriaHttpRequestGenerator.httpMethod(route)
        val path =
            ArmeriaPathVariableSupport.pathWithPlaceholders(
                route.path,
                route.pathType,
                route.explicitExactPath,
            )
        val normalizedPath = path.takeIf { it.startsWith("/") } ?: "/$path"
        val baseUrl = syncedServerBaseUrl(lastSyncedDocsBaseUrl) ?: ArmeriaDocServiceSupport.serverBaseUrl(staticRoutes)
        return "curl -X $method ${shellQuote(baseUrl.trimEnd('/') + normalizedPath)}"
    }

    private fun syncedServerBaseUrl(docsBaseUrl: String?): String? {
        if (docsBaseUrl == null) {
            return null
        }
        val uri = runCatching { URI(docsBaseUrl) }.getOrNull() ?: return null
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val renderedHost = if (':' in host && !host.startsWith("[")) "[$host]" else host
        return buildString {
            append(scheme)
            append("://")
            append(renderedHost)
            if (uri.port >= 0) {
                append(':')
                append(uri.port)
            }
        }
    }

    private fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
}
