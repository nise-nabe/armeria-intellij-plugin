package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import junit.framework.TestCase
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArmeriaRouteTestSupportTest : TestCase() {
    fun testRouteFindsUniqueMatch() {
        val routes =
            listOf(
                testRoute(path = "/a", routeMatch = RouteMatch.ANNOTATED_HTTP),
                testRoute(path = "/b", routeMatch = RouteMatch.SERVICE),
            )

        val route = routes.route(path = "/a", match = RouteMatch.ANNOTATED_HTTP)

        assertEquals("/a", route.path)
    }

    fun testRouteFailsWhenNoMatch() {
        val routes = listOf(testRoute(path = "/only"))

        assertFailsWith<AssertionError> {
            routes.route(path = "/missing")
        }
    }

    fun testRouteFailsWhenMultipleMatch() {
        val routes =
            listOf(
                testRoute(path = "/dup"),
                testRoute(path = "/dup"),
            )

        assertFailsWith<AssertionError> {
            routes.route(path = "/dup")
        }
    }

    fun testSingleRouteSucceedsForOneRoute() {
        val route = listOf(testRoute(path = "/only")).singleRoute()

        assertEquals("/only", route.path)
    }

    fun testSingleRouteFailsForEmptyList() {
        assertFailsWith<AssertionError> {
            emptyList<ArmeriaRoute>().singleRoute()
        }
    }

    fun testSingleRouteFailsForMultipleRoutes() {
        assertFailsWith<AssertionError> {
            listOf(testRoute(path = "/a"), testRoute(path = "/b")).singleRoute()
        }
    }

    fun testAssertRouteRequiresAtLeastOneField() {
        val routes = listOf(testRoute(path = "/only"))

        assertFailsWith<IllegalArgumentException> {
            routes.assertRoute()
        }
    }

    fun testAssertRouteChecksFields() {
        val route =
            listOf(
                testRoute(
                    path = "/api",
                    httpMethod = "POST",
                    routeMatch = RouteMatch.ANNOTATED_HTTP,
                    pathType = PathType.PREFIX,
                ),
            ).assertRoute(
                match = RouteMatch.ANNOTATED_HTTP,
                path = "/api",
                httpMethod = "POST",
                pathType = PathType.PREFIX,
            )

        assertEquals("/api", route.path)
    }

    fun testAssertRouteFailsWhenPathIsAmbiguous() {
        val routes =
            listOf(
                testRoute(path = "/dup", routeMatch = RouteMatch.ANNOTATED_HTTP),
                testRoute(path = "/dup", routeMatch = RouteMatch.SERVICE),
            )

        assertFailsWith<AssertionError> {
            routes.assertRoute(path = "/dup")
        }
    }

    private fun testRoute(
        path: String,
        httpMethod: String = "GET",
        routeMatch: RouteMatch = RouteMatch.ANNOTATED_HTTP,
        pathType: PathType = PathType.EXACT,
    ): ArmeriaRoute =
        ArmeriaRoute(
            protocol = "HTTP",
            httpMethod = httpMethod,
            path = path,
            target = "Handler",
            routeMatch = routeMatch,
            moduleName = "test",
            targetUnresolved = false,
            isDocService = false,
            pathType = pathType,
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
