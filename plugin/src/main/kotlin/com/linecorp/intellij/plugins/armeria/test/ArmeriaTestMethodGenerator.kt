package com.linecorp.intellij.plugins.armeria.test

import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message
import java.util.Locale

enum class ArmeriaTestLanguage {
    JAVA,
    KOTLIN,
}

internal object ArmeriaTestMethodGenerator {
    fun supports(route: ArmeriaRoute): Boolean =
        when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP -> route.httpMethod.isNotBlank()
            RouteMatch.SERVICE, RouteMatch.SERVICE_UNDER -> true
            else -> false
        }

    fun requiresBlockingClient(route: ArmeriaRoute): Boolean = route.executionHints.contains(message("route.explorer.execution.blocking"))

    fun suggestMethodName(route: ArmeriaRoute): String {
        val pathPart = pathToCamelCase(route.path)
        return when (httpMethod(route).uppercase(Locale.ROOT)) {
            "GET" -> if (pathPart.isEmpty()) "rootReturnsSuccess" else "${pathPart}ReturnsSuccess"
            "POST" -> "post${capitalize(pathPart)}"
            else -> "${httpMethod(route).lowercase(Locale.ROOT)}${capitalize(pathPart)}"
        }
    }

    fun generateTestMethod(
        route: ArmeriaRoute,
        serverVariableName: String,
        language: ArmeriaTestLanguage,
    ): String {
        val methodName = suggestMethodName(route)
        val call = httpMethod(route).lowercase(Locale.ROOT)
        val path = ArmeriaRouteSupport.normalizePath(route.path)
        val blocking = requiresBlockingClient(route)
        return when (language) {
            ArmeriaTestLanguage.JAVA ->
                if (blocking) {
                    """
                    @org.junit.jupiter.api.Test
                    void $methodName() {
                        ${ArmeriaJUnitServerExtensionSupport.BLOCKING_WEB_CLIENT_CLASS} client = $serverVariableName.blockingWebClient();
                        com.linecorp.armeria.common.AggregatedHttpResponse response = client.$call("$path").aggregate().join();
                        org.junit.jupiter.api.Assertions.assertEquals(200, response.status().code());
                    }
                    """.trimIndent()
                } else {
                    """
                    @org.junit.jupiter.api.Test
                    void $methodName() {
                        ${ArmeriaJUnitServerExtensionSupport.WEB_CLIENT_CLASS} client = ${ArmeriaJUnitServerExtensionSupport.WEB_CLIENT_CLASS}.of($serverVariableName.httpUri());
                        com.linecorp.armeria.common.AggregatedHttpResponse response = client.$call("$path").aggregate().join();
                        org.junit.jupiter.api.Assertions.assertEquals(200, response.status().code());
                    }
                    """.trimIndent()
                }
            ArmeriaTestLanguage.KOTLIN ->
                if (blocking) {
                    """
                    @Test
                    fun $methodName() {
                        val client = $serverVariableName.blockingWebClient()
                        val response = client.$call("$path").aggregate().join()
                        assertEquals(200, response.status().code())
                    }
                    """.trimIndent()
                } else {
                    """
                    @Test
                    fun $methodName() {
                        val client = WebClient.of($serverVariableName.httpUri())
                        val response = client.$call("$path").aggregate().join()
                        assertEquals(200, response.status().code())
                    }
                    """.trimIndent()
                }
        }
    }

    private fun httpMethod(route: ArmeriaRoute): String =
        when (route.routeMatch) {
            RouteMatch.ANNOTATED_HTTP -> route.httpMethod
            RouteMatch.SERVICE, RouteMatch.SERVICE_UNDER -> "GET"
            else -> error("Unsupported route match: ${route.routeMatch}")
        }

    private fun pathToCamelCase(path: String): String =
        path
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() && !it.startsWith("{") }
            .joinToString("") {
                it
                    .split(Regex("[^a-zA-Z0-9]"))
                    .filter(String::isNotBlank)
                    .joinToString("") { segment -> capitalize(segment.lowercase(Locale.ROOT)) }
            }.replaceFirstChar { if (it.isUpperCase()) it.lowercase(Locale.ROOT) else it.toString() }

    private fun capitalize(value: String): String =
        if (value.isEmpty()) {
            value
        } else {
            value.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
}
