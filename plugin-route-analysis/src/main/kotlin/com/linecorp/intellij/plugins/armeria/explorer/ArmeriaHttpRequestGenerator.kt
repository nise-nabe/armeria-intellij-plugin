package com.linecorp.intellij.plugins.armeria.explorer

import java.util.Locale

object ArmeriaHttpRequestGenerator {
    const val DEFAULT_BASE_URL = "http://localhost:8080"

    private val NON_SLUG_CHARACTERS = Regex("[^a-zA-Z0-9._-]")

    fun supports(route: ArmeriaRoute): Boolean {
        return when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP -> route.httpMethod.isNotBlank()
            RouteMatch.SERVICE, RouteMatch.SERVICE_UNDER -> true
            RouteMatch.RUNTIME -> route.httpMethod.isNotBlank()
            RouteMatch.ANNOTATED_SERVICE, RouteMatch.NON_HTTP -> false
        }
    }

    fun httpMethod(route: ArmeriaRoute): String {
        return when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP, RouteMatch.RUNTIME -> route.httpMethod
            RouteMatch.SERVICE, RouteMatch.SERVICE_UNDER -> "GET"
            RouteMatch.ANNOTATED_SERVICE, RouteMatch.NON_HTTP ->
                error("Unsupported route match: ${route.routeMatch}")
        }
    }

    fun fileName(route: ArmeriaRoute): String {
        val method = httpMethod(route).lowercase(Locale.ROOT)
        return "armeria-$method-${pathSlug(route.path)}.http"
    }

    fun requestText(route: ArmeriaRoute, baseUrl: String = DEFAULT_BASE_URL): String {
        val method = httpMethod(route)
        return buildString {
            appendLine("### ${route.path}")
            appendLine("$method $baseUrl${route.path}")
            appendLine("Accept: application/json")
            appendLine()
        }
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
