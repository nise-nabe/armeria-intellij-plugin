package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.microservices.url.UrlPath
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.endpoints.ArmeriaEndpointUrlPath
import com.linecorp.intellij.plugins.armeria.explorer.endpoints.ArmeriaEndpointsSupport
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaEndpointsSupportTest {
    @Test
    fun toUrlPath_exactRoot() {
        assertEquals(UrlPath.EMPTY, ArmeriaEndpointUrlPath.toUrlPath("/", PathType.EXACT))
        assertEquals(UrlPath.EMPTY, ArmeriaEndpointUrlPath.toUrlPath("", PathType.EXACT))
    }

    @Test
    fun toUrlPath_exactLiteral() {
        val path = ArmeriaEndpointUrlPath.toUrlPath("/hello", PathType.EXACT)

        assertEquals(
            listOf(UrlPath.PathSegment.Exact(""), UrlPath.PathSegment.Exact("hello")),
            path.segments,
        )
    }

    @Test
    fun toUrlPath_braceVariable() {
        val path = ArmeriaEndpointUrlPath.toUrlPath("/users/{id}", PathType.EXACT)

        assertEquals(
            listOf(
                UrlPath.PathSegment.Exact(""),
                UrlPath.PathSegment.Exact("users"),
                UrlPath.PathSegment.Variable("id"),
            ),
            path.segments,
        )
    }

    @Test
    fun toUrlPath_braceVariableWithRegex() {
        val path = ArmeriaEndpointUrlPath.toUrlPath("/users/{id:[0-9]+}", PathType.EXACT)

        assertEquals(
            listOf(
                UrlPath.PathSegment.Exact(""),
                UrlPath.PathSegment.Exact("users"),
                UrlPath.PathSegment.Variable("id", "[0-9]+"),
            ),
            path.segments,
        )
    }

    @Test
    fun toUrlPath_braceVariableWithNestedRegexQuantifier() {
        val path = ArmeriaEndpointUrlPath.toUrlPath("/users/{id:[0-9]{1,3}}", PathType.EXACT)

        assertEquals(
            listOf(
                UrlPath.PathSegment.Exact(""),
                UrlPath.PathSegment.Exact("users"),
                UrlPath.PathSegment.Variable("id", "[0-9]{1,3}"),
            ),
            path.segments,
        )
    }

    @Test
    fun toUrlPath_prefixRoot() {
        assertEquals(
            listOf(UrlPath.PathSegment.Undefined),
            ArmeriaEndpointUrlPath.toUrlPath("/", PathType.PREFIX).segments,
        )
        assertEquals(
            listOf(UrlPath.PathSegment.Undefined),
            ArmeriaEndpointUrlPath.toUrlPath("", PathType.PREFIX).segments,
        )
    }

    @Test
    fun toUrlPath_prefixAppendsUndefined() {
        val path = ArmeriaEndpointUrlPath.toUrlPath("/api", PathType.PREFIX)

        assertEquals(
            listOf(
                UrlPath.PathSegment.Exact(""),
                UrlPath.PathSegment.Exact("api"),
                UrlPath.PathSegment.Undefined,
            ),
            path.segments,
        )
    }

    @Test
    fun toUrlPath_serviceUnderIsPrefix() {
        val path = ArmeriaEndpointUrlPath.toUrlPath("/v1/", PathType.EXACT, RouteMatch.SERVICE_UNDER)

        assertEquals(
            listOf(
                UrlPath.PathSegment.Exact(""),
                UrlPath.PathSegment.Exact("v1"),
                UrlPath.PathSegment.Undefined,
            ),
            path.segments,
        )
    }

    @Test
    fun toUrlPath_colonVariable() {
        val path = ArmeriaEndpointUrlPath.toUrlPath("/users/:id", PathType.EXACT)

        assertEquals(
            listOf(
                UrlPath.PathSegment.Exact(""),
                UrlPath.PathSegment.Exact("users"),
                UrlPath.PathSegment.Variable("id"),
            ),
            path.segments,
        )
    }

    @Test
    fun toUrlPath_globMapsStarToUndefined() {
        val path = ArmeriaEndpointUrlPath.toUrlPath("/files/**", PathType.GLOB)

        assertEquals(
            listOf(
                UrlPath.PathSegment.Exact(""),
                UrlPath.PathSegment.Exact("files"),
                UrlPath.PathSegment.Undefined,
            ),
            path.segments,
        )
    }

    @Test
    fun toUrlPath_regexKeepsExactSegments() {
        val path = ArmeriaEndpointUrlPath.toUrlPath("^(?<id>\\d+)$", PathType.REGEX)

        assertEquals(
            listOf(
                UrlPath.PathSegment.Exact(""),
                UrlPath.PathSegment.Exact("^(?<id>\\d+)$"),
            ),
            path.segments,
        )
    }

    @Test
    fun isVisibleServerRoute_allowsHttpServerMatches() {
        assertTrue(ArmeriaEndpointsSupport.isVisibleServerRoute(route(routeMatch = RouteMatch.ANNOTATED_HTTP)))
        assertTrue(ArmeriaEndpointsSupport.isVisibleServerRoute(route(httpMethod = "", routeMatch = RouteMatch.SERVICE)))
        assertTrue(
            ArmeriaEndpointsSupport.isVisibleServerRoute(
                route(protocol = "WebSocket", httpMethod = "", routeMatch = RouteMatch.SERVICE),
            ),
        )
        assertTrue(ArmeriaEndpointsSupport.isVisibleServerRoute(route(httpMethod = "", routeMatch = RouteMatch.SERVICE_UNDER)))
        assertTrue(ArmeriaEndpointsSupport.isVisibleServerRoute(route(httpMethod = "", routeMatch = RouteMatch.HEALTH_CHECK)))
        assertTrue(ArmeriaEndpointsSupport.isVisibleServerRoute(route(httpMethod = "GET", routeMatch = RouteMatch.ROUTE_FLUENT)))
        assertTrue(ArmeriaEndpointsSupport.isVisibleServerRoute(route(httpMethod = "GET", routeMatch = RouteMatch.CONFIG)))
        assertTrue(
            ArmeriaEndpointsSupport.isVisibleServerRoute(
                route(
                    protocol = "gRPC",
                    path = "/example.Echo/Ping",
                    routeMatch = RouteMatch.NON_HTTP,
                ),
            ),
        )
    }

    @Test
    fun isVisibleServerRoute_rejectsNonUrlAndSpringDelegatedMatches() {
        assertFalse(ArmeriaEndpointsSupport.isVisibleServerRoute(route(httpMethod = "", routeMatch = RouteMatch.ANNOTATED_HTTP)))
        assertFalse(ArmeriaEndpointsSupport.isVisibleServerRoute(route(routeMatch = RouteMatch.DELEGATED)))
        assertFalse(ArmeriaEndpointsSupport.isVisibleServerRoute(route(routeMatch = RouteMatch.ANNOTATED_SERVICE)))
        assertFalse(ArmeriaEndpointsSupport.isVisibleServerRoute(route(routeMatch = RouteMatch.ROUTE_DECORATOR)))
        assertFalse(ArmeriaEndpointsSupport.isVisibleServerRoute(route(routeMatch = RouteMatch.DECORATOR_UNDER)))
        assertFalse(ArmeriaEndpointsSupport.isVisibleServerRoute(route(routeMatch = RouteMatch.VIRTUAL_HOST)))
        assertFalse(ArmeriaEndpointsSupport.isVisibleServerRoute(route(routeMatch = RouteMatch.FILE_SERVICE)))
        assertFalse(ArmeriaEndpointsSupport.isVisibleServerRoute(route(routeMatch = RouteMatch.RUNTIME)))
        assertFalse(
            ArmeriaEndpointsSupport.isVisibleServerRoute(
                route(protocol = "Thrift", path = "/foo", routeMatch = RouteMatch.NON_HTTP),
            ),
        )
    }

    @Test
    fun httpMethods_splitsAndUppercases() {
        assertEquals(listOf("GET", "POST"), ArmeriaEndpointsSupport.httpMethods(route(httpMethod = "GET, POST")))
        assertEquals(listOf("PUT"), ArmeriaEndpointsSupport.httpMethods(route(httpMethod = "HttpMethod.PUT")))
        assertEquals(
            listOf("POST"),
            ArmeriaEndpointsSupport.httpMethods(
                route(
                    protocol = "gRPC",
                    path = "/example.Echo/Ping",
                    routeMatch = RouteMatch.NON_HTTP,
                ),
            ),
        )
    }

    @Test
    fun groupKey_includesModuleName() {
        assertEquals("module:app", ArmeriaEndpointsSupport.groupKey(route(moduleName = "app")))
        assertEquals("module:other", ArmeriaEndpointsSupport.groupKey(route(moduleName = "other")))
    }

    private fun route(
        httpMethod: String = "GET",
        path: String = "/api",
        protocol: String = "HTTP",
        routeMatch: RouteMatch = RouteMatch.ANNOTATED_HTTP,
        moduleName: String = "app",
    ): ArmeriaRoute =
        ArmeriaRoute(
            protocol = protocol,
            httpMethod = httpMethod,
            path = path,
            target = "Handler",
            routeMatch = routeMatch,
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

        override fun getVirtualFile(): VirtualFile? = null

        override fun getPsiRange(): TextRange? = null
    }
}
