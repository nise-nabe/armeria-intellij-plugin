package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaRouteCollectorTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerArmeriaStubs()
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

            public @interface PathPrefix {
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

                public ServerBuilder serviceUnder(String prefix, Object service) {
                    return this;
                }

                public ServerBuilder annotatedService(Object service) {
                    return this;
                }

                public ServerBuilder decorator(Object decorator) {
                    return this;
                }

                public ServerBuilder decorator(String pathPattern, Object decorator) {
                    return this;
                }

                public com.linecorp.armeria.server.Server build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.logging;

            public final class LoggingService {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.cors;

            public final class CorsService {
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcService {
                public static GrpcServiceBuilder builder(Object bindableService) {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.grpc;

            public final class GrpcServiceBuilder {
                public com.linecorp.armeria.server.grpc.GrpcService build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.docs;

            public final class DocService {
                public static DocServiceBuilder builder() {
                    return null;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.docs;

            public final class DocServiceBuilder {
                public com.linecorp.armeria.server.docs.DocService build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }
}
