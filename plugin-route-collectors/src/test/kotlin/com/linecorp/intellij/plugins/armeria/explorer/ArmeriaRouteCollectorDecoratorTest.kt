package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaRouteCollectorDecoratorTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerRouteCollectorStubs()
    }

    fun testCollectProgrammaticDecoratorOnServiceRegistration() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.logging.LoggingService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .decorator(LoggingService.class)
                        .service("/api", new HelloService())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class HelloService {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val serviceRoute = routes.firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals(listOf("Logging"), serviceRoute!!.decorators)
    }

    fun testCollectProgrammaticDecoratorDoesNotBleedAcrossRegistrations() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.logging.LoggingService;
            import com.linecorp.armeria.server.cors.CorsService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .decorator(LoggingService.class)
                        .service("/a", new HelloService())
                        .build();
                    Server.builder()
                        .decorator(CorsService.class)
                        .service("/b", new HelloService())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class HelloService {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val routeA = routes.firstOrNull { it.path == "/a" }
        val routeB = routes.firstOrNull { it.path == "/b" }
        assertNotNull(routeA)
        assertNotNull(routeB)
        assertEquals(listOf("Logging"), routeA!!.decorators)
        assertEquals(listOf("CORS"), routeB!!.decorators)
    }

    fun testCollectPathScopedDecoratorUsesDecoratorArgument() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.logging.LoggingService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .decorator("/api", LoggingService.class)
                        .service("/api", new HelloService())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class HelloService {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val serviceRoute = routes.firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals(listOf("Logging"), serviceRoute!!.decorators)
    }

    fun testCollectPathScopedDecoratorFiltersByRoute() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.logging.LoggingService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .decorator("/api/**", LoggingService.class)
                        .service("/other", new HelloService())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class HelloService {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val otherRoute = routes.firstOrNull { it.path == "/other" }
        assertNotNull(otherRoute)
        assertEquals(emptyList<String>(), otherRoute!!.decorators)
    }

    fun testCollectPathScopedDecoratorWithStaticFinalPathPattern() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.logging.LoggingService;

            public class Main {
                private static final String API_PATH = "/api/**";

                public static void main(String[] args) {
                    Server.builder()
                        .decorator(API_PATH, LoggingService.class)
                        .service("/api", new HelloService())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class HelloService {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val serviceRoute = routes.firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals(listOf("Logging"), serviceRoute!!.decorators)
    }
}
