package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import org.junit.Assert.assertEquals
import org.junit.Test

class ArmeriaRouteExplorerRouteStateTest {
    @Test
    fun applyStatic_keepsPreviouslySyncedRuntimeRoutes() {
        val state = ArmeriaRouteExplorerRouteState()
        val runtime = testRoute(moduleName = "Runtime (DocService)", path = "/runtime")
        val staticBefore = testRoute(moduleName = "app", path = "/old")
        val staticAfter = testRoute(moduleName = "app", path = "/new")

        state.applyStatic(listOf(staticBefore))
        state.applyRuntime(listOf(runtime))
        state.applyStatic(listOf(staticAfter))

        assertEquals(listOf(staticAfter), state.staticRoutes)
        assertEquals(listOf(runtime), state.runtimeRoutes)
        assertEquals(listOf(staticAfter, runtime), state.allRoutes())
    }

    @Test
    fun applyRuntime_replacesPreviousRuntimeRoutes() {
        val state = ArmeriaRouteExplorerRouteState()
        val first = testRoute(moduleName = "Runtime (DocService)", path = "/a")
        val second = testRoute(moduleName = "Runtime (DocService)", path = "/b")

        state.applyRuntime(listOf(first))
        state.applyRuntime(listOf(second))

        assertEquals(listOf(second), state.runtimeRoutes)
    }

    private fun testRoute(
        moduleName: String,
        path: String,
    ): ArmeriaRoute =
        ArmeriaRoute(
            protocol = "HTTP",
            httpMethod = "GET",
            path = path,
            target = "Handler",
            routeMatch = RouteMatch.RUNTIME,
            moduleName = moduleName,
            targetUnresolved = false,
            isDocService = false,
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
