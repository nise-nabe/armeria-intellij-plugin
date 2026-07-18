package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaRouteCollectorServiceRegistrationTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerRouteCollectorStubs()
    }

    fun testCollectServiceRegistrationFromBuilderVariable() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.ServerBuilder;

            public class Main {
                public static void main(String[] args) {
                    ServerBuilder sb = Server.builder();
                    sb.service("/api", new HelloService());
                    sb.build();
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
        assertEquals("example.HelloService", serviceRoute!!.target)
    }

    fun testCollectServiceRegistration() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/api", new HelloService())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/internal")
                public String internal() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val serviceRoute = routes.firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("/api", serviceRoute!!.path)
        assertEquals("example.HelloService", serviceRoute.target)
    }

    fun testCollectGrpcServiceRegistrationWithBuild() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.grpc.GrpcService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/grpc", GrpcService.builder(new HelloGrpcService()).build())
                        .build();
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class HelloGrpcService {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val grpcRoute = routes.firstOrNull { it.routeMatch == RouteMatch.NON_HTTP }
        assertNotNull(grpcRoute)
        assertEquals("example.HelloGrpcService", grpcRoute!!.target)
        assertFalse(grpcRoute.target.equals("build", ignoreCase = true))
    }

    fun testCollectDocServiceRegistrationWithBuild() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.docs.DocService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/docs", DocService.builder().build())
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val docRoute = routes.firstOrNull { it.isDocService }
        assertNotNull(docRoute)
        assertEquals("com.linecorp.armeria.server.docs.DocService", docRoute!!.target)
    }

    fun testCollectUnresolvedNewExpressionTarget() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/api", new MissingService())
                        .build();
                }
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val serviceRoute = routes.firstOrNull { it.path == "/api" }
        assertNotNull(serviceRoute)
        assertTrue(serviceRoute!!.targetUnresolved)
    }

    fun testCollectUnresolvedParenthesizedNewExpressionTarget() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/api", (new MissingService()))
                        .build();
                }
            }
            """.trimIndent(),
        )

        val serviceRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.path == "/api" }
        assertNotNull(serviceRoute)
        assertTrue(serviceRoute!!.targetUnresolved)
    }

    fun testCollectUnresolvedFactoryMethodTarget() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/api", createMissingService())
                        .build();
                }

                private static Object createMissingService() {
                    return new MissingService();
                }
            }
            """.trimIndent(),
        )

        val serviceRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.path == "/api" }
        assertNotNull(serviceRoute)
        assertTrue(serviceRoute!!.targetUnresolved)
    }

    fun testCollectServiceRegistration_requestTimeoutOnBuilderChain() {
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
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import java.time.Duration;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .requestTimeout(Duration.ofSeconds(30))
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

        val route = ArmeriaRouteCollector.collect(project).single { it.path == "/api" }

        assertEquals(listOf("Request timeout: Duration.ofSeconds(30)"), route.timeoutHints)
        assertTrue(route.executionHints.isEmpty())
    }
}
