package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaRouteTreeLabel
import com.linecorp.intellij.plugins.armeria.message
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArmeriaRouteTreeLabelTest {
    @Test
    fun headerMatchSuffix_joinsHeaderMatchHints() {
        val route =
            testRoute(
                contentHints =
                    listOf(
                        message("route.explorer.hint.matchesHeader", "client-type=android"),
                        message("route.explorer.hint.statusCode", "201"),
                    ),
            )

        assertEquals(
            message("route.explorer.hint.matchesHeader", "client-type=android"),
            ArmeriaRouteTreeLabel.headerMatchSuffix(route),
        )
        assertTrue(route.speedSearchText.contains("client-type=android"))
    }

    @Test
    fun headerMatchSuffix_isNullWithoutHeaderHints() {
        val route = testRoute(contentHints = listOf(message("route.explorer.hint.statusCode", "200")))
        assertNull(ArmeriaRouteTreeLabel.headerMatchSuffix(route))
    }

    private fun testRoute(contentHints: List<String>): ArmeriaRoute =
        ArmeriaRoute(
            protocol = "HTTP",
            httpMethod = "GET",
            path = "/users/{id}",
            target = "Handler",
            routeMatch = RouteMatch.ANNOTATED_HTTP,
            moduleName = "app",
            targetUnresolved = false,
            isDocService = false,
            decorators = emptyList(),
            exceptionHandlers = emptyList(),
            contentHints = contentHints,
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
