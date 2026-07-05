package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaExtendedRegistrationCollectorTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerArmeriaStubs()
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

            public @interface Path {
                String value();
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

                public ServerBuilder fileService(String path, java.io.File root) {
                    return this;
                }

                public ServerBuilder healthCheckService() {
                    return this;
                }

                public ServerBuilder virtualHost(String hostname) {
                    return this;
                }

                public ServerBuilder route() {
                    return this;
                }

                public ServerBuilder post(String path) {
                    return this;
                }

                public ServerBuilder build(Object handler) {
                    return this;
                }

                public ServerBuilder decoratorUnder(String path, Object decorator) {
                    return this;
                }

                public com.linecorp.armeria.server.Server build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }
}
