package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaRouteDetailFormatterTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerRouteDetailFormatterStubs()
    }

    fun testRegistrationSummary_annotatedRoute() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/users/{id}")
                public String getUser() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )
        val route = ArmeriaRouteCollector.collect(project).single()
        assertTrue(ArmeriaRouteDetailFormatter.registrationSummary(route).contains("@GET"))
        assertTrue(ArmeriaRouteDetailFormatter.registrationSummary(route).contains("/users/{id}"))
    }

    fun testAttachmentsLine_includesExecutionHints() {
        val route =
            ArmeriaRoute(
                protocol = "HTTP",
                httpMethod = "GET",
                path = "/hello",
                target = "example.HelloService",
                routeMatch = RouteMatch.ANNOTATED_HTTP,
                moduleName = "app",
                targetUnresolved = false,
                isDocService = false,
                annotatedServiceHasPathPrefix = false,
                decorators = emptyList(),
                exceptionHandlers = emptyList(),
                executionHints = listOf(message("route.explorer.execution.blocking")),
                pointer = TestPsiPointer,
            )

        val attachments = ArmeriaRouteDetailFormatter.attachmentsLine(route)

        assertTrue(attachments.contains(message("route.explorer.detail.execution", message("route.explorer.execution.blocking"))))
    }

    fun testAttachmentsLine_includesTimeoutHints() {
        val route =
            ArmeriaRoute(
                protocol = "HTTP",
                httpMethod = "GET",
                path = "/hello",
                target = "example.HelloService",
                routeMatch = RouteMatch.SERVICE,
                moduleName = "app",
                targetUnresolved = false,
                isDocService = false,
                annotatedServiceHasPathPrefix = false,
                decorators = emptyList(),
                exceptionHandlers = emptyList(),
                timeoutHints = listOf("5s"),
                pointer = TestPsiPointer,
            )

        val attachments = ArmeriaRouteDetailFormatter.attachmentsLine(route)

        assertTrue(attachments.contains(message("route.explorer.detail.timeouts", "5s")))
    }

    fun testAttachmentsLine_omitsSecondarySeparator() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Decorator;
            import com.linecorp.armeria.server.annotation.ExceptionHandler;
            import com.linecorp.armeria.server.annotation.Get;

            @Decorator(MyDecorator.class)
            @ExceptionHandler(MyHandler.class)
            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }

            class MyDecorator {}
            class MyHandler {}
            """.trimIndent(),
        )

        val route = ArmeriaRouteCollector.collect(project).single()
        val attachments = ArmeriaRouteDetailFormatter.attachmentsLine(route)

        assertFalse(attachments.startsWith(" · "))
        assertTrue(attachments.contains("decorators:"))
        assertTrue(attachments.contains("handlers:"))
        assertTrue(attachments.contains("MyDecorator"))
        assertTrue(attachments.contains("MyHandler"))
    }

    fun testRegistrationSummary_runtimeRoute() {
        val route =
            ArmeriaRoute(
                protocol = "DocService (runtime)",
                httpMethod = "GET",
                path = "/api/users/{id}",
                target = "com.example.FooService/getUser",
                routeMatch = RouteMatch.RUNTIME,
                moduleName = "Runtime (DocService)",
                targetUnresolved = false,
                isDocService = false,
                decorators = emptyList(),
                exceptionHandlers = emptyList(),
                pointer = TestPsiPointer,
            )
        assertEquals(
            "GET /api/users/{id} (runtime)",
            ArmeriaRouteDetailFormatter.registrationSummary(route),
        )
        assertTrue(ArmeriaRouteDetailFormatter.statusLine(route).contains("Runtime"))
    }

    fun testRegistrationSummary_annotatedServiceWithoutPathPrefix() {
        val route =
            ArmeriaRoute(
                protocol = "HTTP",
                httpMethod = "",
                path = "/",
                target = "example.HelloService",
                routeMatch = RouteMatch.ANNOTATED_SERVICE,
                moduleName = "app",
                targetUnresolved = false,
                isDocService = false,
                annotatedServiceHasPathPrefix = false,
                decorators = emptyList(),
                exceptionHandlers = emptyList(),
                pointer = TestPsiPointer,
            )
        assertEquals(
            "Server.builder().annotatedService(…)",
            ArmeriaRouteDetailFormatter.registrationSummary(route),
        )
    }

    fun testRegistrationSummary_annotatedServiceWithPathPrefix() {
        val route =
            ArmeriaRoute(
                protocol = "HTTP",
                httpMethod = "",
                path = "/api",
                target = "example.HelloService",
                routeMatch = RouteMatch.ANNOTATED_SERVICE,
                moduleName = "app",
                targetUnresolved = false,
                isDocService = false,
                annotatedServiceHasPathPrefix = true,
                decorators = emptyList(),
                exceptionHandlers = emptyList(),
                pointer = TestPsiPointer,
            )
        assertEquals(
            "Server.builder().annotatedService(\"/api\", …)",
            ArmeriaRouteDetailFormatter.registrationSummary(route),
        )
    }

    fun testRegisteredInHint_serviceRegistration() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                void configure() {
                    Server.builder().service("/api", new HelloService());
                }
            }

            class HelloService {}
            """.trimIndent(),
        )
        val route = ArmeriaRouteCollector.collect(project).single()
        assertEquals("example.Main#configure()", route.resolveRegisteredInHint())
        assertEquals("Server.builder().service(\"/api\", …)", ArmeriaRouteDetailFormatter.registrationSummary(route))
    }

    fun testAttachmentsLine_includesVirtualHostName() {
        val route =
            ArmeriaRoute(
                protocol = "HTTP",
                httpMethod = "GET",
                path = "/api",
                target = "example.ApiService",
                routeMatch = RouteMatch.SERVICE,
                moduleName = "app",
                targetUnresolved = false,
                isDocService = false,
                annotatedServiceHasPathPrefix = false,
                decorators = emptyList(),
                exceptionHandlers = emptyList(),
                virtualHostName = "api.example.com",
                pointer = TestPsiPointer,
            )

        val attachments = ArmeriaRouteDetailFormatter.attachmentsLine(route)

        assertEquals(
            message("route.explorer.detail.virtualHost", "api.example.com"),
            attachments,
        )
    }

    fun testAttachmentsLine_includesPathType() {
        val route =
            ArmeriaRoute(
                protocol = "HTTP",
                httpMethod = "GET",
                path = "/api/**",
                target = "example.ApiService",
                routeMatch = RouteMatch.SERVICE_UNDER,
                pathType = PathType.GLOB,
                moduleName = "app",
                targetUnresolved = false,
                isDocService = false,
                annotatedServiceHasPathPrefix = false,
                decorators = emptyList(),
                exceptionHandlers = emptyList(),
                pointer = TestPsiPointer,
            )

        val attachments = ArmeriaRouteDetailFormatter.attachmentsLine(route)

        assertEquals(
            message("route.explorer.detail.pathType", message("route.explorer.pathType.glob")),
            attachments,
        )
    }

    fun testAttachmentsLine_includesContentHints() {
        val route =
            ArmeriaRoute(
                protocol = "HTTP",
                httpMethod = "POST",
                path = "/users/{id}",
                target = "example.UserService",
                routeMatch = RouteMatch.ANNOTATED_HTTP,
                moduleName = "app",
                targetUnresolved = false,
                isDocService = false,
                annotatedServiceHasPathPrefix = false,
                decorators = emptyList(),
                exceptionHandlers = emptyList(),
                contentHints =
                    listOf(
                        message("route.explorer.hint.statusCode", "201"),
                        message("route.explorer.hint.consumes", "application/json"),
                    ),
                pointer = TestPsiPointer,
            )

        val attachments = ArmeriaRouteDetailFormatter.attachmentsLine(route)

        assertEquals(
            message(
                "route.explorer.detail.content",
                route.contentHints.joinToString(" · "),
            ),
            attachments,
        )
    }

    fun testRegistrationSummary_extendedRouteMatches() {
        assertEquals(
            "Server.builder().fileService(\"/files/\", …)",
            ArmeriaRouteDetailFormatter.registrationSummary(
                ArmeriaRoute(
                    protocol = "HTTP",
                    httpMethod = "",
                    path = "/files/",
                    target = "/tmp",
                    routeMatch = RouteMatch.FILE_SERVICE,
                    moduleName = "app",
                    targetUnresolved = false,
                    isDocService = false,
                    annotatedServiceHasPathPrefix = false,
                    decorators = emptyList(),
                    exceptionHandlers = emptyList(),
                    pointer = TestPsiPointer,
                ),
            ),
        )
        assertEquals(
            "Server.builder().healthCheckService() at /internal/healthcheck",
            ArmeriaRouteDetailFormatter.registrationSummary(
                ArmeriaRoute(
                    protocol = "HTTP",
                    httpMethod = "GET",
                    path = "/internal/healthcheck",
                    target = message("route.explorer.target.healthCheck"),
                    routeMatch = RouteMatch.HEALTH_CHECK,
                    moduleName = "app",
                    targetUnresolved = false,
                    isDocService = false,
                    annotatedServiceHasPathPrefix = false,
                    decorators = emptyList(),
                    exceptionHandlers = emptyList(),
                    pointer = TestPsiPointer,
                ),
            ),
        )
        assertEquals(
            "Server.builder().virtualHost(\"api.example.com\")",
            ArmeriaRouteDetailFormatter.registrationSummary(
                ArmeriaRoute(
                    protocol = "HTTP",
                    httpMethod = "",
                    path = "/",
                    target = "api.example.com",
                    routeMatch = RouteMatch.VIRTUAL_HOST,
                    moduleName = "app",
                    targetUnresolved = false,
                    isDocService = false,
                    annotatedServiceHasPathPrefix = false,
                    decorators = emptyList(),
                    exceptionHandlers = emptyList(),
                    virtualHostName = "api.example.com",
                    pointer = TestPsiPointer,
                ),
            ),
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
