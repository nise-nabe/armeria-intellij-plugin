package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaExtendedRegistrationCollectorBasicTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerExtendedRegistrationCollectorStubs()
    }

    fun testCollectFileServiceRegistration() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import java.io.File;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .fileService("/files/", new File("/tmp"))
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val fileRoute = routes.firstOrNull { it.routeMatch == RouteMatch.FILE_SERVICE }
        assertNotNull(fileRoute)
        assertEquals("/files/", fileRoute!!.path)
    }

    fun testCollectFileServiceWithJavaConstantPath() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import java.io.File;

            public class Main {
                private static final String FILES_PATH = "/files/";

                public static void main(String[] args) {
                    Server.builder()
                        .fileService(FILES_PATH, new File("/tmp"))
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val fileRoute = routes.firstOrNull { it.routeMatch == RouteMatch.FILE_SERVICE }
        assertNotNull(fileRoute)
        assertEquals("/files/", fileRoute!!.path)
    }

    fun testCollectHealthCheckRegistration() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .healthCheckService()
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val healthRoute = routes.firstOrNull { it.routeMatch == RouteMatch.HEALTH_CHECK }
        assertNotNull(healthRoute)
        assertEquals("/internal/healthcheck", healthRoute!!.path)
    }

    fun testCollectFluentRouteRegistration() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .route()
                        .post("/api/items")
                        .build((ctx, req) -> null)
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val fluentRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ROUTE_FLUENT }
        assertNotNull(fluentRoute)
        assertEquals("POST", fluentRoute!!.httpMethod)
        assertEquals("/api/items", fluentRoute.path)
    }

    fun testCollectDecoratorUnderRegistration() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.logging.LoggingService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .decoratorUnder("/public", LoggingService.newDecorator())
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val decoratorRoute = routes.firstOrNull { it.routeMatch == RouteMatch.DECORATOR_UNDER }
        assertNotNull(decoratorRoute)
        assertEquals("/public", decoratorRoute!!.path)
    }

    fun testCollectPathAnnotationAndPathType() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Path;

            public class HelloService {
                @Get
                @Path("prefix:/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val route = routes.single()
        assertEquals(PathType.PREFIX, route.pathType)
        assertEquals("/hello", route.path)
    }

    fun testCollectRegexPathAnnotationTrimsWhitespace() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Path;

            public class HelloService {
                @Get
                @Path("regex: /foo")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val route = routes.single()
        assertEquals(PathType.REGEX, route.pathType)
        assertEquals("/foo", route.path)
    }

    fun testCollectGlobPathAnnotationNormalizesLeadingSlash() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Path;

            public class HelloService {
                @Get
                @Path("glob:foo/**")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val route = routes.single()
        assertEquals(PathType.GLOB, route.pathType)
        assertEquals("/foo/**", route.path)
    }

    fun testCollectFluentRoutePathPrefix() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .route()
                        .pathPrefix("/api")
                        .get("/items")
                        .build((ctx, req) -> null)
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val fluentRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ROUTE_FLUENT }
        assertNotNull(fluentRoute)
        assertEquals(PathType.EXACT, fluentRoute!!.pathType)
        assertEquals("/api/items", fluentRoute.path)
        assertEquals("GET", fluentRoute.httpMethod)
    }
}
