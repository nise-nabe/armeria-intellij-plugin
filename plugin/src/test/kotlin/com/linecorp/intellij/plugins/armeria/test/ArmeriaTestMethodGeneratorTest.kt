package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArmeriaTestMethodGeneratorTest {
    @Test
    fun supportsAnnotatedHttpRoute() {
        assertTrue(ArmeriaTestMethodGenerator.supports(route(httpMethod = "GET")))
    }

    @Test
    fun supportsServiceRoute() {
        assertTrue(ArmeriaTestMethodGenerator.supports(route(routeMatch = RouteMatch.SERVICE, httpMethod = "")))
    }

    @Test
    fun rejectsNonHttpRoute() {
        assertFalse(ArmeriaTestMethodGenerator.supports(route(routeMatch = RouteMatch.NON_HTTP, httpMethod = "")))
    }

    @Test
    fun suggestMethodNameForGetPath() {
        val name = ArmeriaTestMethodGenerator.suggestMethodName(route(path = "/users/{id}"))
        assertEquals("usersReturnsSuccess", name)
    }

    @Test
    fun generateJavaTestMethodUsesBlockingClientWhenRouteIsBlocking() {
        val generated =
            ArmeriaTestMethodGenerator.generateTestMethod(
                route = route(path = "/slow", executionHints = listOf(message("route.explorer.execution.blocking"))),
                serverVariableName = "server",
                language = ArmeriaTestLanguage.JAVA,
            )
        assertTrue(generated.contains("blockingWebClient"))
    }

    @Test
    fun generateJavaTestMethodEscapesPathCharacters() {
        val generated =
            ArmeriaTestMethodGenerator.generateTestMethod(
                route = route(path = "/api\"quoted"),
                serverVariableName = "server",
                language = ArmeriaTestLanguage.JAVA,
            )
        assertTrue(generated.contains("\"/api\\\"quoted\""))
    }

    private fun route(
        httpMethod: String = "GET",
        path: String = "/api",
        routeMatch: RouteMatch = RouteMatch.ANNOTATED_HTTP,
        executionHints: List<String> = emptyList(),
    ): ArmeriaRoute =
        ArmeriaRoute(
            protocol = "HTTP",
            httpMethod = httpMethod,
            path = path,
            target = "Handler",
            routeMatch = routeMatch,
            moduleName = "app",
            targetUnresolved = false,
            isDocService = false,
            decorators = emptyList(),
            exceptionHandlers = emptyList(),
            executionHints = executionHints,
            pointer = EmptyPointer,
        )

    private object EmptyPointer : SmartPsiElementPointer<PsiElement> {
        override fun getElement(): PsiElement? = null

        override fun getContainingFile(): PsiFile? = null

        override fun getRange(): TextRange? = null

        override fun getProject(): Project = throw UnsupportedOperationException()

        override fun getVirtualFile(): VirtualFile = throw UnsupportedOperationException()

        override fun getPsiRange(): TextRange? = null
    }
}
