package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.test.FastTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.experimental.categories.Category

@Category(FastTest::class)
class ArmeriaHttpRequestGeneratorTest {
    @Test
    fun supports_annotatedHttpRouteWithMethod() {
        val route = route(httpMethod = "POST", routeMatch = RouteMatch.ANNOTATED_HTTP)

        assertTrue(ArmeriaHttpRequestGenerator.supports(route))
    }

    @Test
    fun supports_rejectsAnnotatedHttpRouteWithoutMethod() {
        val route = route(httpMethod = "", routeMatch = RouteMatch.ANNOTATED_HTTP)

        assertFalse(ArmeriaHttpRequestGenerator.supports(route))
    }

    @Test
    fun supports_serviceRouteWithBlankMethod() {
        val route = route(httpMethod = "", routeMatch = RouteMatch.SERVICE)

        assertTrue(ArmeriaHttpRequestGenerator.supports(route))
    }

    @Test
    fun supports_serviceUnderRouteWithBlankMethod() {
        val route = route(httpMethod = "", path = "/v1", routeMatch = RouteMatch.SERVICE_UNDER)

        assertTrue(ArmeriaHttpRequestGenerator.supports(route))
    }

    @Test
    fun supports_rejectsNonHttpRoutes() {
        assertFalse(ArmeriaHttpRequestGenerator.supports(route(routeMatch = RouteMatch.ANNOTATED_SERVICE)))
        assertFalse(
            ArmeriaHttpRequestGenerator.supports(
                route(protocol = "gRPC", routeMatch = RouteMatch.NON_HTTP),
            ),
        )
    }

    @Test
    fun httpMethod_defaultsServiceRoutesToGet() {
        assertEquals("GET", ArmeriaHttpRequestGenerator.httpMethod(route(routeMatch = RouteMatch.SERVICE)))
        assertEquals("GET", ArmeriaHttpRequestGenerator.httpMethod(route(path = "/v1", routeMatch = RouteMatch.SERVICE_UNDER)))
    }

    @Test
    fun httpMethod_usesAnnotatedHttpMethod() {
        assertEquals("PATCH", ArmeriaHttpRequestGenerator.httpMethod(route(httpMethod = "PATCH")))
    }

    @Test
    fun fileName_includesMethodAndPathSlug() {
        val route = route(httpMethod = "POST", path = "/api/users/{id}")

        assertEquals("armeria-post-api-users--id-.http", ArmeriaHttpRequestGenerator.fileName(route))
    }

    @Test
    fun fileName_usesRootSlugForEmptyPath() {
        assertEquals("armeria-get-root.http", ArmeriaHttpRequestGenerator.fileName(route(path = "/")))
    }

    @Test
    fun fileName_distinguishesMethodsOnSamePath() {
        val getRoute = route(httpMethod = "GET", path = "/api/users")
        val postRoute = route(httpMethod = "POST", path = "/api/users")

        assertEquals("armeria-get-api-users.http", ArmeriaHttpRequestGenerator.fileName(getRoute))
        assertEquals("armeria-post-api-users.http", ArmeriaHttpRequestGenerator.fileName(postRoute))
    }

    @Test
    fun requestText_includesMethodPathAndDefaultHost() {
        val route = route(httpMethod = "POST", path = "/api/users")

        assertEquals(
            """
            ### /api/users
            POST http://localhost:8080/api/users
            Accept: application/json

            """.trimIndent() + "\n",
            ArmeriaHttpRequestGenerator.requestText(route),
        )
    }

    private fun route(
        httpMethod: String = "GET",
        path: String = "/api",
        protocol: String = "HTTP",
        routeMatch: RouteMatch = RouteMatch.ANNOTATED_HTTP,
    ): ArmeriaRoute {
        return ArmeriaRoute(
            protocol = protocol,
            httpMethod = httpMethod,
            path = path,
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
