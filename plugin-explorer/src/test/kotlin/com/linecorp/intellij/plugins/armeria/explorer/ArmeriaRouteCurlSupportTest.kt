package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ArmeriaRouteCurlSupportTest {
    @Test
    fun curlText_usesDetectedStaticListenPort() {
        val command =
            ArmeriaRouteCurlSupport.curlText(
                route = route(path = "/users/{id}"),
                staticRoutes = listOf(listenPort(":9090", "HTTP")),
                lastSyncedDocsBaseUrl = null,
            )

        assertEquals("curl -X GET 'http://localhost:9090/users/{id}'", command)
    }

    @Test
    fun curlText_prefersSyncedServerOrigin() {
        val command =
            ArmeriaRouteCurlSupport.curlText(
                route = route(path = "/users/{id}"),
                staticRoutes = listOf(listenPort(":9090", "HTTP")),
                lastSyncedDocsBaseUrl = "https://example.test:8443/docs",
            )

        assertEquals("curl -X GET 'https://example.test:8443/users/{id}'", command)
    }

    @Test
    fun curlText_preservesExplicitExactPath() {
        val command =
            ArmeriaRouteCurlSupport.curlText(
                route = route(path = "/status/:ready", explicitExactPath = true),
                staticRoutes = emptyList(),
                lastSyncedDocsBaseUrl = null,
            )

        assertEquals("curl -X GET 'http://localhost:8080/status/:ready'", command)
    }

    @Test
    fun curlText_preservesIpv6SyncedHostBrackets() {
        val command =
            ArmeriaRouteCurlSupport.curlText(
                route = route(path = "/api"),
                staticRoutes = emptyList(),
                lastSyncedDocsBaseUrl = "http://[::1]:8080/docs",
            )

        assertEquals("curl -X GET 'http://[::1]:8080/api'", command)
    }

    @Test
    fun curlText_returnsNullForNonHttpRoutes() {
        assertNull(
            ArmeriaRouteCurlSupport.curlText(
                route = route(routeMatch = RouteMatch.NON_HTTP),
                staticRoutes = emptyList(),
                lastSyncedDocsBaseUrl = null,
            ),
        )
    }

    private fun listenPort(
        path: String,
        protocol: String,
    ): ArmeriaRoute = route(path = path, protocol = protocol, routeMatch = RouteMatch.LISTEN_PORT)

    private fun route(
        path: String = "/api",
        protocol: String = "HTTP",
        routeMatch: RouteMatch = RouteMatch.SERVICE,
        explicitExactPath: Boolean = false,
    ): ArmeriaRoute =
        ArmeriaRoute(
            protocol = protocol,
            httpMethod = "GET",
            path = path,
            target = "Handler",
            routeMatch = routeMatch,
            moduleName = "app",
            targetUnresolved = false,
            isDocService = false,
            decorators = emptyList(),
            exceptionHandlers = emptyList(),
            pointer = TestPsiPointer,
            explicitExactPath = explicitExactPath,
        )

    private object TestPsiPointer : SmartPsiElementPointer<PsiElement> {
        override fun getElement(): PsiElement? = null

        override fun getContainingFile(): PsiFile? = null

        override fun getRange(): TextRange? = null

        override fun getProject(): Project = throw UnsupportedOperationException()

        override fun getVirtualFile(): VirtualFile = throw UnsupportedOperationException()

        override fun getPsiRange(): TextRange? = null
    }
}
