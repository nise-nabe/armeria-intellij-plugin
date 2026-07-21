package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaHttpRequestGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun supports_healthCheckAndFluentRoutes() {
        assertTrue(ArmeriaHttpRequestGenerator.supports(route(routeMatch = RouteMatch.HEALTH_CHECK)))
        assertTrue(ArmeriaHttpRequestGenerator.supports(route(httpMethod = "POST", routeMatch = RouteMatch.ROUTE_FLUENT)))
    }

    @Test
    fun supports_configRouteWithMethod() {
        val route = route(httpMethod = "GET", path = "/internal/healthcheck", routeMatch = RouteMatch.CONFIG)

        assertTrue(ArmeriaHttpRequestGenerator.supports(route))
        assertEquals("GET", ArmeriaHttpRequestGenerator.httpMethod(route))
    }

    @Test
    fun supports_rejectsConfigRouteWithoutMethod() {
        assertFalse(ArmeriaHttpRequestGenerator.supports(route(httpMethod = "", routeMatch = RouteMatch.CONFIG)))
    }

    @Test
    fun supports_delegatedRoute() {
        val route = route(httpMethod = "GET", routeMatch = RouteMatch.DELEGATED)

        assertTrue(ArmeriaHttpRequestGenerator.supports(route))
        assertEquals("GET", ArmeriaHttpRequestGenerator.httpMethod(route))
        assertEquals(
            "GET",
            ArmeriaHttpRequestGenerator.httpMethod(route(httpMethod = "", routeMatch = RouteMatch.DELEGATED)),
        )
    }

    @Test
    fun supports_rejectsExtendedNonRequestRoutes() {
        assertFalse(ArmeriaHttpRequestGenerator.supports(route(routeMatch = RouteMatch.FILE_SERVICE)))
        assertFalse(ArmeriaHttpRequestGenerator.supports(route(routeMatch = RouteMatch.VIRTUAL_HOST)))
        assertFalse(ArmeriaHttpRequestGenerator.supports(route(routeMatch = RouteMatch.ROUTE_DECORATOR)))
        assertFalse(ArmeriaHttpRequestGenerator.supports(route(routeMatch = RouteMatch.DECORATOR_UNDER)))
    }

    @Test
    fun httpMethod_defaultsHealthCheckAndFluentRoutesToGet() {
        assertEquals("GET", ArmeriaHttpRequestGenerator.httpMethod(route(routeMatch = RouteMatch.HEALTH_CHECK)))
        assertEquals("GET", ArmeriaHttpRequestGenerator.httpMethod(route(httpMethod = "", routeMatch = RouteMatch.ROUTE_FLUENT)))
        assertEquals("POST", ArmeriaHttpRequestGenerator.httpMethod(route(httpMethod = "POST", routeMatch = RouteMatch.ROUTE_FLUENT)))
    }

    @Test
    fun supports_rejectsNonHttpRoutes() {
        assertFalse(ArmeriaHttpRequestGenerator.supports(route(routeMatch = RouteMatch.ANNOTATED_SERVICE)))
        assertFalse(
            ArmeriaHttpRequestGenerator.supports(
                route(protocol = "Thrift", routeMatch = RouteMatch.NON_HTTP),
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

    @Test
    fun requestText_substitutesPathVariables() {
        val route = route(httpMethod = "GET", path = "/users/{id}")

        assertTrue(ArmeriaHttpRequestGenerator.requestText(route).contains("/users/1"))
    }

    @Test
    fun supports_rejectsGrpcRegistrationMount() {
        val route = route(protocol = "gRPC", path = "/grpc", routeMatch = RouteMatch.NON_HTTP)

        assertFalse(ArmeriaHttpRequestGenerator.supports(route))
    }

    @Test
    fun requestText_substitutesColonStylePathVariables() {
        val route = route(httpMethod = "GET", path = "/hello/:name")

        assertTrue(ArmeriaHttpRequestGenerator.requestText(route).contains("/hello/example"))
    }

    @Test
    fun supports_grpcRoute() {
        val route = route(protocol = "gRPC", path = "/example.EchoService/Echo", routeMatch = RouteMatch.NON_HTTP)

        assertTrue(ArmeriaHttpRequestGenerator.supports(route))
        assertEquals("armeria-grpc-example.EchoService-Echo.http", ArmeriaHttpRequestGenerator.fileName(route))
    }

    @Test
    fun supports_grpcRouteWithoutPackage() {
        val route =
            route(
                protocol = "gRPC",
                path = "/Greeter/Ping",
                target = "Greeter.Ping",
                routeMatch = RouteMatch.NON_HTTP,
            )

        assertTrue(ArmeriaHttpRequestGenerator.supports(route))
        assertEquals("armeria-grpc-Greeter-Ping.http", ArmeriaHttpRequestGenerator.fileName(route))
    }

    @Test
    fun requestText_grpcProtoRoute() {
        val route =
            route(
                protocol = "gRPC",
                path = "/example.EchoService/Echo",
                target = "example.EchoService.Echo",
                routeMatch = RouteMatch.NON_HTTP,
            )

        assertEquals(
            """
            ### gRPC example.EchoService.Echo
            GRPC http://localhost:8080/example.EchoService/Echo

            # Invoke via DocService: http://localhost:8080/docs

            """.trimIndent() + "\n",
            ArmeriaHttpRequestGenerator.requestText(route),
        )
    }

    @Test
    fun requestText_preservesRegexPath() {
        val route = route(httpMethod = "GET", path = """\d{2,3}""", pathType = PathType.REGEX)

        assertTrue(ArmeriaHttpRequestGenerator.requestText(route).contains("""http://localhost:8080\d{2,3}"""))
    }

    @Test
    fun requestText_substitutesConstrainedPathVariables() {
        val route = route(httpMethod = "GET", path = "/users/{id:\\d+}")

        assertTrue(ArmeriaHttpRequestGenerator.requestText(route).contains("/users/1"))
    }

    @Test
    fun requestText_substitutesConstrainedPathVariablesWithWhitespace() {
        val route = route(httpMethod = "GET", path = "/users/{id :\\d+}")

        assertTrue(ArmeriaHttpRequestGenerator.requestText(route).contains("/users/1"))
    }

    @Test
    fun requestText_substitutesConstrainedPathVariablesWithQuantifierBraces() {
        val route = route(httpMethod = "GET", path = "/users/{id:\\d{2,3}}")

        assertTrue(ArmeriaHttpRequestGenerator.requestText(route).contains("/users/1"))
        assertFalse(ArmeriaHttpRequestGenerator.requestText(route).contains("/users/1}"))
    }

    @Test
    fun requestText_grpcProtoRouteWithoutPackage() {
        val route =
            route(
                protocol = "gRPC",
                path = "/Greeter/Ping",
                target = "Greeter.Ping",
                routeMatch = RouteMatch.NON_HTTP,
            )

        assertEquals(
            """
            ### gRPC Greeter.Ping
            GRPC http://localhost:8080/Greeter/Ping

            # Invoke via DocService: http://localhost:8080/docs

            """.trimIndent() + "\n",
            ArmeriaHttpRequestGenerator.requestText(route),
        )
    }

    @Test
    fun httpMethod_errorsForUnsupportedNonHttpRoute() {
        val route = route(protocol = "Thrift", routeMatch = RouteMatch.NON_HTTP)

        val error = runCatching { ArmeriaHttpRequestGenerator.httpMethod(route) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("NON_HTTP"))
    }

    @Test
    fun requestText_grpcProtoRouteNormalizesTrailingSlashBaseUrl() {
        val route =
            route(
                protocol = "gRPC",
                path = "/example.EchoService/Echo",
                target = "example.EchoService.Echo",
                routeMatch = RouteMatch.NON_HTTP,
            )

        assertEquals(
            """
            ### gRPC example.EchoService.Echo
            GRPC http://localhost:8080/example.EchoService/Echo

            # Invoke via DocService: http://localhost:8080/docs

            """.trimIndent() + "\n",
            ArmeriaHttpRequestGenerator.requestText(route, "http://localhost:8080/"),
        )
    }

    private fun route(
        httpMethod: String = "GET",
        path: String = "/api",
        protocol: String = "HTTP",
        routeMatch: RouteMatch = RouteMatch.ANNOTATED_HTTP,
        pathType: PathType = PathType.EXACT,
        target: String = "Handler",
    ): ArmeriaRoute =
        ArmeriaRoute(
            protocol = protocol,
            httpMethod = httpMethod,
            path = path,
            target = target,
            routeMatch = routeMatch,
            moduleName = "app",
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
