package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaTestMethodGeneratorSupportsParameterizedTest {
    @ParameterizedTest
    @CsvSource(
        "ANNOTATED_HTTP, GET, true",
        "SERVICE, , false",
        "NON_HTTP, , false",
    )
    fun supportsAnnotatedHttpRoutes(
        routeMatch: RouteMatch,
        httpMethod: String?,
        expected: Boolean,
    ) {
        val actual =
            ArmeriaTestMethodGenerator.supports(
                route(
                    routeMatch = routeMatch,
                    httpMethod = httpMethod.orEmpty(),
                ),
            )
        if (expected) {
            assertTrue(actual)
        } else {
            assertFalse(actual)
        }
    }

    @ParameterizedTest
    @CsvSource(
        "/users/{id}, usersReturnsSuccess",
    )
    fun suggestMethodNameForPaths(
        path: String,
        expected: String,
    ) {
        assertEquals(expected, ArmeriaTestMethodGenerator.suggestMethodName(route(path = path)))
    }

    private fun route(
        httpMethod: String = "GET",
        path: String = "/api",
        routeMatch: RouteMatch = RouteMatch.ANNOTATED_HTTP,
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
            executionHints = emptyList(),
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
