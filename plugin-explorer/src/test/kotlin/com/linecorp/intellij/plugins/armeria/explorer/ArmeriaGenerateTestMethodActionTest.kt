package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.TestActionEvent
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaTestMethodGeneration
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaGenerateTestMethodActionTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    fun testActionDisabledWhenRouteIsNull() {
        val action = ArmeriaGenerateTestMethodAction({ null }, FakeGeneration())
        val event = TestActionEvent.createTestEvent(action)
        ActionUtil.updateAction(action, event)
        assertFalse(event.presentation.isEnabled)
    }

    fun testActionUsesInjectedGenerationSupport() {
        val generation = FakeGeneration(supported = true)
        val route = route(path = "/api")
        val action = ArmeriaGenerateTestMethodAction({ route }, generation)
        val event = TestActionEvent.createTestEvent(action)
        ActionUtil.updateAction(action, event)
        assertTrue(event.presentation.isEnabled)

        action.actionPerformed(event)
        assertEquals(listOf("/api"), generation.insertedPaths)
    }

    private class FakeGeneration(
        private val supported: Boolean = false,
    ) : ArmeriaTestMethodGeneration {
        val insertedPaths = mutableListOf<String>()

        override fun supports(route: ArmeriaRoute): Boolean = supported

        override fun insertFromRouteExplorer(
            project: Project,
            route: ArmeriaRoute,
        ): Boolean = error("Route Explorer actions must use asynchronous insertion")

        override fun insertFromRouteExplorerAsync(
            project: Project,
            route: ArmeriaRoute,
        ) {
            insertedPaths += route.path
        }
    }

    private fun route(path: String): ArmeriaRoute =
        ArmeriaRoute(
            protocol = "HTTP",
            httpMethod = "GET",
            path = path,
            target = "Handler",
            routeMatch = RouteMatch.ANNOTATED_HTTP,
            moduleName = module.name,
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

        override fun getProject() = throw UnsupportedOperationException()

        override fun getVirtualFile(): VirtualFile = throw UnsupportedOperationException()

        override fun getPsiRange(): TextRange? = null
    }
}
