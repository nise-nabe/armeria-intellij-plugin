package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceSupport
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArmeriaDocServiceSupportTest {
    @Test
    fun hasDocService_detectsDocServiceRoutes() {
        assertTrue(ArmeriaDocServiceSupport.hasDocService(listOf(docServiceRoute())))
        assertFalse(ArmeriaDocServiceSupport.hasDocService(listOf(httpRoute())))
    }

    @Test
    fun url_buildsFromRoutePathAndBaseUrl() {
        val route = docServiceRoute(path = "/docs")

        assertEquals("http://localhost:8080/docs", ArmeriaDocServiceSupport.url(route))
        assertEquals("http://127.0.0.1:9090/docs", ArmeriaDocServiceSupport.url(route, "http://127.0.0.1:9090"))
    }

    @Test
    fun url_normalizesBaseUrlAndPath() {
        val route = docServiceRoute(path = "docservice")

        assertEquals("http://localhost:8080/docservice", ArmeriaDocServiceSupport.url(route, "http://localhost:8080/"))
    }

    @Test
    fun primaryUrl_returnsFirstDocServiceRoute() {
        val routes = listOf(httpRoute(), docServiceRoute(path = "/docs"), docServiceRoute(path = "/other-docs"))

        assertEquals("http://localhost:8080/docs", ArmeriaDocServiceSupport.primaryUrl(routes))
    }

    @Test
    fun primaryUrl_returnsNullWhenNoDocServiceRoutes() {
        assertNull(ArmeriaDocServiceSupport.primaryUrl(listOf(httpRoute())))
    }

    @Test
    fun listenPortBinding_detectsHttpAndHttpsPorts() {
        assertEquals(8080 to false, ArmeriaDocServiceSupport.listenPortBinding(listOf(listenPort(":8080", "HTTP"))))
        assertEquals(8443 to true, ArmeriaDocServiceSupport.listenPortBinding(listOf(listenPort(":8443", "HTTPS"))))
    }

    @Test
    fun listenPortBinding_prefersLowestDetectedPort() {
        val routes = listOf(listenPort(":9090", "HTTP"), listenPort(":8080", "HTTP"))

        assertEquals(8080 to false, ArmeriaDocServiceSupport.listenPortBinding(routes))
    }

    @Test
    fun listenPortBindings_returnsDistinctDetectedPortsInOrder() {
        val routes =
            listOf(
                listenPort(":9090", "HTTP"),
                listenPort(":8080", "HTTP"),
                listenPort(":8443", "HTTPS"),
                listenPort(":9090", "HTTP"),
            )

        assertEquals(
            listOf(8080 to false, 8443 to true, 9090 to false),
            ArmeriaDocServiceSupport.listenPortBindings(routes),
        )
    }

    @Test
    fun docsBaseUrl_usesDetectedListenPortAndScheme() {
        val routes = listOf(listenPort(":8443", "HTTPS"), docServiceRoute(path = "/docs"))

        assertEquals("https://localhost:8443/docs", ArmeriaDocServiceSupport.docsBaseUrl(routes))
    }

    @Test
    fun docsBaseUrl_ignoresNonListenPortRoutes() {
        val routes = listOf(httpRoute(), docServiceRoute(path = "/docs"))

        assertEquals("http://localhost:8080/docs", ArmeriaDocServiceSupport.docsBaseUrl(routes))
    }

    private fun docServiceRoute(path: String = "/docs"): ArmeriaRoute = route(path = path, isDocService = true)

    private fun httpRoute(): ArmeriaRoute = route()

    private fun listenPort(
        path: String,
        protocol: String,
    ): ArmeriaRoute = route(path = path, protocol = protocol, routeMatch = RouteMatch.LISTEN_PORT)

    private fun route(
        path: String = "/api",
        isDocService: Boolean = false,
        protocol: String? = null,
        routeMatch: RouteMatch = RouteMatch.SERVICE,
    ): ArmeriaRoute =
        ArmeriaRoute(
            protocol = protocol ?: if (isDocService) "DocService" else "HTTP",
            httpMethod = "GET",
            path = path,
            target = "Handler",
            routeMatch = routeMatch,
            moduleName = "app",
            targetUnresolved = false,
            isDocService = isDocService,
            decorators = emptyList(),
            exceptionHandlers = emptyList(),
            pointer = TestPsiPointer,
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
