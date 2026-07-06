package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmeriaObservabilitySummaryTest {
    @Test
    fun summarize_listsMatchedDecoratorsAndHealthCheck() {
        val routes = listOf(
            route(
                decorators = listOf(
                    message("route.explorer.decorator.logging"),
                    message("route.explorer.decorator.brave"),
                ),
            ),
            route(routeMatch = RouteMatch.HEALTH_CHECK, path = "/internal/healthcheck"),
        )

        val summary = ArmeriaObservabilitySummary.summarize(routes)

        assertTrue(summary.contains(message("route.explorer.observability.logging")))
        assertTrue(summary.contains(message("route.explorer.observability.tracing")))
        assertTrue(summary.contains(message("route.explorer.observability.healthCheck")))
    }

    @Test
    fun summarize_matchesDecoratorClassSimpleNames() {
        val routes = listOf(route(decorators = listOf("LoggingService", "BraveService")))

        val summary = ArmeriaObservabilitySummary.summarize(routes)

        assertTrue(summary.contains(message("route.explorer.observability.logging")))
        assertTrue(summary.contains(message("route.explorer.observability.tracing")))
    }

    @Test
    fun summarize_includesHealthCheckWhenOnlySignal() {
        val routes = listOf(route(routeMatch = RouteMatch.HEALTH_CHECK, path = "/internal/healthcheck"))

        val summary = ArmeriaObservabilitySummary.summarize(routes)

        assertTrue(summary.contains(message("route.explorer.observability.healthCheck")))
    }

    @Test
    fun summarize_returnsEmptyWhenNoSignals() {
        val routes = listOf(route(decorators = listOf("Cors")))

        assertEquals("", ArmeriaObservabilitySummary.summarize(routes))
    }

    @Test
    fun summarize_omitsDocServiceEvenWhenDecorated() {
        val routes = listOf(
            route(
                isDocService = true,
                routeMatch = RouteMatch.NON_HTTP,
                protocol = "gRPC",
                decorators = listOf(message("route.explorer.decorator.logging")),
            ),
        )

        assertEquals("", ArmeriaObservabilitySummary.summarize(routes))
    }

    @Test
    fun summarize_doesNotTreatOAuthAsAuth() {
        val routes = listOf(route(decorators = listOf("OAuthService")))

        assertEquals("", ArmeriaObservabilitySummary.summarize(routes))
    }

    private fun route(
        decorators: List<String> = emptyList(),
        isDocService: Boolean = false,
        protocol: String = "HTTP",
        routeMatch: RouteMatch = RouteMatch.SERVICE,
        path: String = "/api",
    ): ArmeriaRoute {
        return ArmeriaRoute(
            protocol = protocol,
            httpMethod = "GET",
            path = path,
            target = "Handler",
            routeMatch = routeMatch,
            moduleName = "app",
            targetUnresolved = false,
            isDocService = isDocService,
            decorators = decorators,
            exceptionHandlers = emptyList(),
            pointer = TestPsiPointer,
        )
    }

    private object TestPsiPointer : SmartPsiElementPointer<PsiElement> {
        override fun getElement(): PsiElement? = null

        override fun getContainingFile(): PsiFile? = null

        override fun getRange(): TextRange? = null

        override fun getProject(): Project = throw UnsupportedOperationException()

        override fun getVirtualFile(): VirtualFile = throw UnsupportedOperationException()

        override fun getPsiRange(): TextRange? = null
    }
}
