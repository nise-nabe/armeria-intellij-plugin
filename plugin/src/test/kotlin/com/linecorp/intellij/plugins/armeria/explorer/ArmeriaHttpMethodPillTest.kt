package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmeriaHttpMethodPillTest {
    @Test
    fun pillLabel_usesHttpMethodForAnnotatedRoutes() {
        val route = route(httpMethod = "POST", routeMatch = RouteMatch.ANNOTATED_HTTP)

        assertEquals("POST", ArmeriaHttpMethodPill.pillLabel(route))
    }

    @Test
    fun pillLabel_abbreviatesNonAnnotatedRoutes() {
        assertEquals("ANN", ArmeriaHttpMethodPill.pillLabel(route(routeMatch = RouteMatch.ANNOTATED_SERVICE)))
        assertEquals("ALL", ArmeriaHttpMethodPill.pillLabel(route(routeMatch = RouteMatch.SERVICE)))
        assertEquals("PRE", ArmeriaHttpMethodPill.pillLabel(route(routeMatch = RouteMatch.SERVICE_UNDER)))
        assertEquals("GRPC", ArmeriaHttpMethodPill.pillLabel(route(protocol = "gRPC", routeMatch = RouteMatch.NON_HTTP)))
    }

    @Test
    fun pillText_wrapsLabelWithSpaces() {
        assertEquals(" GET ", ArmeriaHttpMethodPill.pillText("GET"))
    }

    @Test
    fun isStandardHttpMethod_recognizesArmeriaAnnotations() {
        assertTrue(ArmeriaHttpMethodPill.isStandardHttpMethod("GET"))
        assertTrue(ArmeriaHttpMethodPill.isStandardHttpMethod("PATCH"))
        assertFalse(ArmeriaHttpMethodPill.isStandardHttpMethod("ALL"))
    }

    private fun route(
        httpMethod: String = "GET",
        protocol: String = "HTTP",
        routeMatch: RouteMatch = RouteMatch.ANNOTATED_HTTP,
    ): ArmeriaRoute {
        return ArmeriaRoute(
            protocol = protocol,
            httpMethod = httpMethod,
            path = "/api",
            target = "Handler",
            routeMatch = routeMatch,
            moduleName = "app",
            targetUnresolved = false,
            isDocService = false,
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
