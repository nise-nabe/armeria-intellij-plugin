package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.DelegationKind
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaHttpMethodPill
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
    fun pillLabel_abbreviatesExtendedRegistrationRoutes() {
        assertEquals("FIL", ArmeriaHttpMethodPill.pillLabel(route(routeMatch = RouteMatch.FILE_SERVICE)))
        assertEquals("HLT", ArmeriaHttpMethodPill.pillLabel(route(routeMatch = RouteMatch.HEALTH_CHECK)))
        assertEquals("VHS", ArmeriaHttpMethodPill.pillLabel(route(routeMatch = RouteMatch.VIRTUAL_HOST)))
        assertEquals("DEC", ArmeriaHttpMethodPill.pillLabel(route(routeMatch = RouteMatch.ROUTE_DECORATOR)))
        assertEquals("UND", ArmeriaHttpMethodPill.pillLabel(route(routeMatch = RouteMatch.DECORATOR_UNDER)))
    }

    @Test
    fun pillLabel_usesHttpMethodForFluentRoutes() {
        assertEquals("POST", ArmeriaHttpMethodPill.pillLabel(route(httpMethod = "POST", routeMatch = RouteMatch.ROUTE_FLUENT)))
        assertEquals("ALL", ArmeriaHttpMethodPill.pillLabel(route(httpMethod = "", routeMatch = RouteMatch.ROUTE_FLUENT)))
    }

    @Test
    fun pillLabel_abbreviatesNonAnnotatedRoutes() {
        assertEquals("ANN", ArmeriaHttpMethodPill.pillLabel(route(routeMatch = RouteMatch.ANNOTATED_SERVICE)))
        assertEquals("ALL", ArmeriaHttpMethodPill.pillLabel(route(routeMatch = RouteMatch.SERVICE)))
        assertEquals("PRE", ArmeriaHttpMethodPill.pillLabel(route(routeMatch = RouteMatch.SERVICE_UNDER)))
        assertEquals("GRPC", ArmeriaHttpMethodPill.pillLabel(route(protocol = "gRPC", routeMatch = RouteMatch.NON_HTTP)))
        assertEquals("DELETE", ArmeriaHttpMethodPill.pillLabel(route(httpMethod = "DELETE", routeMatch = RouteMatch.RUNTIME)))
    }

    @Test
    fun pillLabel_usesHttpMethodOrMvcFallbackForDelegatedRoutes() {
        assertEquals(
            "POST",
            ArmeriaHttpMethodPill.pillLabel(
                route(
                    httpMethod = "POST",
                    routeMatch = RouteMatch.DELEGATED,
                    delegationKind = DelegationKind.SPRING_MVC,
                ),
            ),
        )
        assertEquals(
            "MVC",
            ArmeriaHttpMethodPill.pillLabel(
                route(
                    httpMethod = "",
                    routeMatch = RouteMatch.DELEGATED,
                    delegationKind = DelegationKind.SPRING_MVC,
                ),
            ),
        )
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
        delegationKind: DelegationKind? = null,
    ): ArmeriaRoute =
        ArmeriaRoute(
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
            delegationKind = delegationKind,
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
