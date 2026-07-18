package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaRouteCollectorAnnotatedRouteTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerRouteCollectorStubs()
    }

    fun testCollectAnnotatedRoute() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertEquals(1, routes.size)
        val route = routes.single()
        assertEquals("/hello", route.path)
        assertEquals("GET", route.httpMethod)
        assertEquals(RouteMatch.ANNOTATED_HTTP, route.routeMatch)
        assertTrue(route.target.contains("HelloService#hello"))
    }

    fun testCollectAnnotatedServiceRegistration() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .annotatedService(new HelloService())
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val registrationRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ANNOTATED_SERVICE }
        assertNotNull(registrationRoute)
        assertEquals("/", registrationRoute!!.path)
        assertFalse(registrationRoute.annotatedServiceHasPathPrefix)
        assertEquals(
            "Server.builder().annotatedService(…)",
            ArmeriaRouteDetailFormatter.registrationSummary(registrationRoute),
        )

        val annotatedMethodRoute = routes.firstOrNull { it.path == "/hello" }
        assertNotNull(annotatedMethodRoute)
        assertEquals(RouteMatch.ANNOTATED_HTTP, annotatedMethodRoute!!.routeMatch)
    }

    fun testCollectPathPrefix() {
        myFixture.configureByText(
            "PrefixedService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.PathPrefix;

            @PathPrefix("/v1")
            public class PrefixedService {
                @Get("/items")
                public String items() {
                    return "items";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertEquals(1, routes.size)
        assertEquals("/v1/items", routes.single().path)
    }

    fun testCollectAnnotatedRoute_blockingOnMethod() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Blocking {
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Blocking;
            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Blocking
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val route = ArmeriaRouteCollector.collect(project).single()

        assertEquals(listOf("Blocking"), route.executionHints)
        assertTrue(route.timeoutHints.isEmpty())
    }

    fun testCollectAnnotatedRoute_blockingOnClass() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Blocking {
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Blocking;
            import com.linecorp.armeria.server.annotation.Get;

            @Blocking
            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val route = ArmeriaRouteCollector.collect(project).single()

        assertEquals(listOf("Blocking"), route.executionHints)
    }

    fun testCollectAnnotatedRoute_doesNotAttachUnrelatedFileTimeouts() {
        myFixture.addClass(
            """
            package java.time;

            public final class Duration {
                public static Duration ofSeconds(long seconds) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Mixed.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.annotation.Get;
            import java.time.Duration;

            public class Mixed {
                public static void configure() {
                    Server.builder().requestTimeout(Duration.ofSeconds(30)).build();
                }
            }

            class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }
            }
            """.trimIndent(),
        )

        val route = ArmeriaRouteCollector.collect(project).single { it.path == "/hello" }

        assertTrue(route.executionHints.isEmpty())
        assertTrue(route.timeoutHints.isEmpty())
    }
}
