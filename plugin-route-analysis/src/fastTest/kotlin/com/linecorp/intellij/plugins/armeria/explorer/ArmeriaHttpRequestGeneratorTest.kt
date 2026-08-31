package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.GrpcRouteHint
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaHttpRequestGenerator
import com.linecorp.intellij.plugins.armeria.message
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
    fun supports_rejectsWebSocketRoutes() {
        assertFalse(
            ArmeriaHttpRequestGenerator.supports(
                route(protocol = "WebSocket", path = "/chat", routeMatch = RouteMatch.NON_HTTP),
            ),
        )
        assertFalse(
            ArmeriaHttpRequestGenerator.supports(
                route(protocol = "WebSocket", path = "/chat", routeMatch = RouteMatch.SERVICE),
            ),
        )
    }

    @Test
    fun requestText_sseServiceUsesEventStreamAccept() {
        val route =
            route(
                protocol = "SSE",
                httpMethod = "GET",
                path = "/events",
                routeMatch = RouteMatch.SERVICE,
                contentHints = listOf(message("route.explorer.hint.produces", "text/event-stream")),
            )

        assertTrue(ArmeriaHttpRequestGenerator.supports(route))
        assertTrue(ArmeriaHttpRequestGenerator.requestText(route).contains("Accept: text/event-stream"))
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
    fun requestText_keepsBracePathVariablePlaceholders() {
        val route = route(httpMethod = "GET", path = "/users/{id}")

        assertTrue(ArmeriaHttpRequestGenerator.requestText(route).contains("/users/{id}"))
    }

    @Test
    fun supports_rejectsGrpcRegistrationMount() {
        val route = route(protocol = "gRPC", path = "/grpc", routeMatch = RouteMatch.NON_HTTP)

        assertFalse(ArmeriaHttpRequestGenerator.supports(route))
    }

    @Test
    fun supports_rejectsGraphqlServiceMount() {
        val route =
            route(
                protocol = "GraphQL",
                path = "/graphql",
                target = "com.linecorp.armeria.server.graphql.GraphqlService",
                routeMatch = RouteMatch.NON_HTTP,
            )

        assertFalse(ArmeriaHttpRequestGenerator.supports(route))
    }

    @Test
    fun requestText_convertsColonStylePathVariablesToPlaceholders() {
        val route = route(httpMethod = "GET", path = "/hello/:name")

        assertTrue(ArmeriaHttpRequestGenerator.requestText(route).contains("/hello/{name}"))
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

            # Invoke via DocService: http://localhost:8080/docs/#/methods/example.EchoService/Echo
            # gRPC-JSON uses POST with a JSON body:
            {}

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
    fun requestText_stripsConstraintsFromBracePathVariables() {
        val route = route(httpMethod = "GET", path = "/users/{id:\\d+}")

        assertTrue(ArmeriaHttpRequestGenerator.requestText(route).contains("/users/{id}"))
    }

    @Test
    fun requestText_stripsConstrainedPathVariablesWithWhitespace() {
        val route = route(httpMethod = "GET", path = "/users/{id :\\d+}")

        assertTrue(ArmeriaHttpRequestGenerator.requestText(route).contains("/users/{id}"))
    }

    @Test
    fun requestText_stripsConstrainedPathVariablesWithQuantifierBraces() {
        val route = route(httpMethod = "GET", path = "/users/{id:\\d{2,3}}")

        assertTrue(ArmeriaHttpRequestGenerator.requestText(route).contains("/users/{id}"))
        assertFalse(ArmeriaHttpRequestGenerator.requestText(route).contains("/users/{id}}"))
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

            # Invoke via DocService: http://localhost:8080/docs/#/methods/Greeter/Ping
            # gRPC-JSON uses POST with a JSON body:
            {}

            """.trimIndent() + "\n",
            ArmeriaHttpRequestGenerator.requestText(route),
        )
    }

    @Test
    fun httpMethod_errorsForUnsupportedNonHttpRoute() {
        val route = route(protocol = "Thrift", routeMatch = RouteMatch.NON_HTTP)

        val error = runCatching { ArmeriaHttpRequestGenerator.httpMethod(route) }.exceptionOrNull()

        assertNotNull(error)
        assertTrue(error is IllegalStateException)
        assertTrue(assertNotNull(error.message).contains("NON_HTTP"))
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

            # Invoke via DocService: http://localhost:8080/docs/#/methods/example.EchoService/Echo
            # gRPC-JSON uses POST with a JSON body:
            {}

            """.trimIndent() + "\n",
            ArmeriaHttpRequestGenerator.requestText(route, "http://localhost:8080/"),
        )
    }

    @Test
    fun requestText_unframedGrpcProtoRouteUsesPostJson() {
        val route =
            route(
                protocol = "gRPC",
                path = "/grpc.hello.HelloService/Hello",
                target = "grpc.hello.HelloService.Hello",
                routeMatch = RouteMatch.NON_HTTP,
                contentHints = listOf(GrpcRouteHint.UNFRAMED),
            )

        assertEquals(
            """
            ### gRPC grpc.hello.HelloService.Hello
            POST http://localhost:8080/grpc.hello.HelloService/Hello
            Content-Type: application/json
            Accept: application/json

            # Invoke via DocService: http://localhost:8080/docs/#/methods/grpc.hello.HelloService/Hello
            # ${message("route.explorer.http.grpcProtobufAlternate")}
            {}

            """.trimIndent() + "\n",
            ArmeriaHttpRequestGenerator.requestText(route),
        )
    }

    @Test
    fun requestText_framedGrpcProtoRouteKeepsGrpcPlaceholder() {
        val route =
            route(
                protocol = "gRPC",
                path = "/grpc.hello.HelloService/Hello",
                target = "grpc.hello.HelloService.Hello",
                routeMatch = RouteMatch.NON_HTTP,
            )

        val text = ArmeriaHttpRequestGenerator.requestText(route)

        assertTrue(text.contains("GRPC http://localhost:8080/grpc.hello.HelloService/Hello"))
        assertFalse(text.contains("POST http://localhost:8080/grpc.hello.HelloService/Hello"))
    }

    @Test
    fun requestText_usesConsumesProducesAndMatchesHeader() {
        val route =
            route(
                httpMethod = "POST",
                path = "/users/{id}",
                contentHints =
                    listOf(
                        message("route.explorer.hint.matchesHeader", "client-type=android"),
                        message("route.explorer.hint.matchesHeader", "x.foo=bar"),
                        message("route.explorer.hint.matchesHeader", "x~foo=bar"),
                        message("route.explorer.hint.matchesHeader", "x!foo=bar"),
                        message("route.explorer.hint.matchesHeader", "authorization"),
                        message("route.explorer.hint.matchesHeader", "env!=prod"),
                        message("route.explorer.hint.consumes", "application/xml, application/json"),
                        message("route.explorer.hint.produces", "application/json"),
                    ),
            )

        assertEquals(
            """
            ### /users/{id}
            POST http://localhost:8080/users/{id}
            client-type: android
            x.foo: bar
            x~foo: bar
            x!foo: bar
            Content-Type: application/json
            Accept: application/json

            {}
            """.trimIndent() + "\n",
            ArmeriaHttpRequestGenerator.requestText(route),
        )
    }

    @Test
    fun requestText_includesDescriptionCommentsAndMatchesParam() {
        val route =
            route(
                httpMethod = "GET",
                path = "/items",
                contentHints =
                    listOf(
                        message("route.explorer.hint.matchesParam", "env=prod"),
                        message("route.explorer.hint.matchesParam", "debug!=true"),
                        message("route.explorer.hint.description", "Lists items."),
                        message("route.explorer.hint.description", "Shared catalog."),
                    ),
            )

        assertEquals(
            """
            ### /items
            # Lists items.
            # Shared catalog.
            GET http://localhost:8080/items?env=prod
            Accept: application/json
            """.trimIndent() + "\n\n",
            ArmeriaHttpRequestGenerator.requestText(route),
        )
    }

    @Test
    fun requestText_omitsBodyOnGetEvenWithConsumes() {
        val route =
            route(
                httpMethod = "GET",
                path = "/items",
                contentHints = listOf(message("route.explorer.hint.consumes", "application/json")),
            )

        val text = ArmeriaHttpRequestGenerator.requestText(route)
        assertFalse(text.contains("Content-Type:"))
        assertFalse(text.contains("{}"))
    }

    @Test
    fun supports_graphqlRoute() {
        val route =
            route(
                protocol = "GraphQL",
                path = "/graphql",
                target = "Query.user",
                routeMatch = RouteMatch.NON_HTTP,
            )

        assertTrue(ArmeriaHttpRequestGenerator.supports(route))
        assertEquals("POST", ArmeriaHttpRequestGenerator.httpMethod(route))
        assertEquals("armeria-graphql-Query.user.http", ArmeriaHttpRequestGenerator.fileName(route))
    }

    @Test
    fun requestText_graphqlQueryStub() {
        val route =
            route(
                protocol = "GraphQL",
                path = "/graphql",
                target = "Query.user",
                routeMatch = RouteMatch.NON_HTTP,
            )

        assertEquals(
            """
            ### Query.user
            POST http://localhost:8080/graphql
            Content-Type: application/json
            Accept: application/json

            {"query": "query { user }"}
            """.trimIndent() + "\n",
            ArmeriaHttpRequestGenerator.requestText(route),
        )
    }

    @Test
    fun requestText_graphqlMutationStub() {
        val route =
            route(
                protocol = "GraphQL",
                path = "/graphql",
                target = "Mutation.createUser",
                routeMatch = RouteMatch.NON_HTTP,
            )

        assertTrue(ArmeriaHttpRequestGenerator.requestText(route).contains("""{"query": "mutation { createUser }"}"""))
    }

    @Test
    fun requestText_usesDocServiceExampleHeadersAndBody() {
        val route =
            route(
                httpMethod = "POST",
                path = "/hello",
                target = "example.HelloService#hello()",
                exampleRequests = listOf("""{"name":"Armeria"}"""),
                exampleHeaders = listOf("authorization: bearer-token"),
            )

        assertEquals(
            """
            ### /hello
            POST http://localhost:8080/hello
            authorization: bearer-token
            Content-Type: application/json
            Accept: application/json

            {"name":"Armeria"}
            """.trimIndent() + "\n",
            ArmeriaHttpRequestGenerator.requestText(route),
        )
    }

    @Test
    fun requestText_doesNotDuplicateContentTypeFromExampleHeaders() {
        val route =
            route(
                httpMethod = "POST",
                path = "/hello",
                exampleRequests = listOf("name=Armeria"),
                exampleHeaders = listOf("Content-Type: application/x-www-form-urlencoded"),
            )

        val text = ArmeriaHttpRequestGenerator.requestText(route)
        assertEquals(1, text.lines().count { it.startsWith("Content-Type:", ignoreCase = true) })
        assertTrue(text.contains("Content-Type: application/x-www-form-urlencoded"))
    }

    @Test
    fun requestText_grpcUsesExampleBodyAndDebugFormUrl() {
        val route =
            route(
                protocol = "gRPC",
                path = "/example.EchoService/Echo",
                target = "example.EchoService.Echo",
                routeMatch = RouteMatch.NON_HTTP,
                exampleRequests = listOf("""{"message":"hi"}"""),
                exampleHeaders = listOf("authorization: bearer-token"),
            )

        val text = ArmeriaHttpRequestGenerator.requestText(route)
        assertTrue(text.contains("# Invoke via DocService: http://localhost:8080/docs/#/methods/example.EchoService/Echo"))
        assertTrue(text.contains("# authorization: bearer-token"))
        assertTrue(text.contains("""{"message":"hi"}"""))
    }

    private fun route(
        httpMethod: String = "GET",
        path: String = "/api",
        protocol: String = "HTTP",
        routeMatch: RouteMatch = RouteMatch.ANNOTATED_HTTP,
        pathType: PathType = PathType.EXACT,
        target: String = "Handler",
        contentHints: List<String> = emptyList(),
        exampleRequests: List<String> = emptyList(),
        exampleHeaders: List<String> = emptyList(),
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
            contentHints = contentHints,
            pointer = TestPsiPointer,
            exampleRequests = exampleRequests,
            exampleHeaders = exampleHeaders,
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
