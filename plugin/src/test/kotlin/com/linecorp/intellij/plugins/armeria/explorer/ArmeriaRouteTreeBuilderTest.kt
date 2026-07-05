package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.test.FastTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.experimental.categories.Category
import javax.swing.tree.DefaultMutableTreeNode

@Category(FastTest::class)
class ArmeriaRouteTreeBuilderTest {
    @Test
    fun buildRoot_groupsRoutesByModule() {
        val routes = listOf(
            testRoute(moduleName = "app", path = "/a"),
            testRoute(moduleName = "app", path = "/b"),
            testRoute(moduleName = "lib", path = "/c"),
        )

        val root = ArmeriaRouteTreeBuilder.buildRoot(routes)

        assertEquals(2, root.childCount)
        val appModule = root.getChildAt(0) as DefaultMutableTreeNode
        val libModule = root.getChildAt(1) as DefaultMutableTreeNode
        assertEquals("app", (appModule.userObject as ArmeriaRouteTreeBuilder.ModuleNode).name)
        assertEquals(2, appModule.childCount)
        assertEquals("lib", (libModule.userObject as ArmeriaRouteTreeBuilder.ModuleNode).name)
        assertEquals(1, libModule.childCount)
    }

    @Test
    fun selectedRoute_returnsRouteNodeOnly() {
        val route = testRoute(moduleName = "app", path = "/health")
        val routeNode = DefaultMutableTreeNode(ArmeriaRouteTreeBuilder.RouteNode(route))
        val moduleNode = DefaultMutableTreeNode(ArmeriaRouteTreeBuilder.ModuleNode("app", 1))

        assertEquals(route, ArmeriaRouteTreeBuilder.selectedRoute(routeNode))
        assertNull(ArmeriaRouteTreeBuilder.selectedRoute(moduleNode))
    }

    private fun testRoute(moduleName: String, path: String): ArmeriaRoute {
        return ArmeriaRoute(
            protocol = "HTTP",
            httpMethod = "GET",
            path = path,
            target = "Handler",
            routeMatch = RouteMatch.ANNOTATED_HTTP,
            moduleName = moduleName,
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
