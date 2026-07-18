package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaExtendedRegistrationCollectorRouteDecoratorTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerExtendedRegistrationCollectorStubs()
    }

    fun testCollectRouteDecoratorDefaultPathTypeIsGlob() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.logging.LoggingService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .routeDecorator()
                        .build(LoggingService.newDecorator())
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val decoratorRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ROUTE_DECORATOR }
        assertNotNull(decoratorRoute)
        assertEquals(PathType.GLOB, decoratorRoute!!.pathType)
        assertEquals("/**", decoratorRoute.path)
    }

    fun testCollectRouteDecoratorMethodsStripHttpMethodPrefix() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.common.HttpMethod;
            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.logging.LoggingService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .routeDecorator()
                        .methods(HttpMethod.POST, HttpMethod.PUT)
                        .build(LoggingService.newDecorator())
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val decoratorRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ROUTE_DECORATOR }
        assertNotNull(decoratorRoute)
        assertEquals("POST, PUT", decoratorRoute!!.httpMethod)
    }

    fun testCollectRouteDecoratorIgnoresUnrelatedCallsInSameBlock() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.logging.LoggingService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .routeDecorator()
                        .path("/decorated/**")
                        .build(LoggingService.newDecorator())
                        .build();
                    System.out.println("started");
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val decoratorRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ROUTE_DECORATOR }
        assertNotNull(decoratorRoute)
        assertEquals("/decorated/**", decoratorRoute!!.path)
        assertEquals(1, routes.count { it.routeMatch == RouteMatch.ROUTE_DECORATOR })
    }

    fun testCollectRouteDecoratorIgnoresPathCallsInsideDecoratorLambda() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.logging.LoggingService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .routeDecorator()
                        .path("/decorated/**")
                        .build(decoratorWithNestedPath(LoggingService.newDecorator()))
                        .build();
                }

                private static Object decoratorWithNestedPath(Object decorator) {
                    LoggingService.builder().path("/wrong/**").build();
                    return decorator;
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val decoratorRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ROUTE_DECORATOR }
        assertNotNull(decoratorRoute)
        assertEquals("/decorated/**", decoratorRoute!!.path)
    }

    fun testCollectRouteDecoratorRegistration() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.logging.LoggingService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .routeDecorator()
                        .path("/decorated/**")
                        .build(LoggingService.newDecorator())
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val decoratorRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ROUTE_DECORATOR }
        assertNotNull(decoratorRoute)
    }
}
