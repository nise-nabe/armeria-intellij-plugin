package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private fun docServiceRoute(path: String = "/docs"): ArmeriaRoute {
        return route(path = path, isDocService = true)
    }

    private fun httpRoute(): ArmeriaRoute {
        return route()
    }

    private fun route(
        path: String = "/api",
        isDocService: Boolean = false,
    ): ArmeriaRoute {
        return ArmeriaRoute(
            protocol = if (isDocService) "DocService" else "HTTP",
            httpMethod = "GET",
            path = path,
            target = "Handler",
            routeMatch = RouteMatch.SERVICE,
            moduleName = "app",
            targetUnresolved = false,
            isDocService = isDocService,
            decorators = emptyList(),
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
