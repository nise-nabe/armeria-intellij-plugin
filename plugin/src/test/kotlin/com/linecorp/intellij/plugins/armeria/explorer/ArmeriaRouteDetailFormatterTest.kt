package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

import com.linecorp.intellij.plugins.armeria.message

class ArmeriaRouteDetailFormatterTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerArmeriaStubs()
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
        val route = ArmeriaRoute(
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
            executionHints = listOf(message("route.explorer.timeout.blocking")),
            pointer = TestPsiPointer,
        )

        val attachments = ArmeriaRouteDetailFormatter.attachmentsLine(route)

        assertTrue(attachments.contains(message("route.explorer.detail.execution", message("route.explorer.timeout.blocking"))))
        assertFalse(attachments.contains(message("route.explorer.detail.timeouts", message("route.explorer.timeout.blocking"))))
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
        val route = ArmeriaRoute(
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
        val route = ArmeriaRoute(
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
        val route = ArmeriaRoute(
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

    private fun registerArmeriaStubs() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Get {
                String value() default "";
                String path() default "";
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Decorator {
                Class<?>[] value();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface ExceptionHandler {
                Class<?>[] value();
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server;

            public final class Server {
                public static ServerBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server;

            public final class ServerBuilder {
                public ServerBuilder service(String path, Object service) {
                    return this;
                }
            }
            """.trimIndent(),
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
