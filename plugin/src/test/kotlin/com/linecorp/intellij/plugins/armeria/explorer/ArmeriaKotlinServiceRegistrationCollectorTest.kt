package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaKotlinServiceRegistrationCollectorTest : ArmeriaFixtureTestBase() {
    fun testCollectServiceRegistrationFromBuilderVariable() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.ServerBuilder

            fun main() {
                val sb: ServerBuilder = Server.builder()
                sb.service("/api", HelloService())
                sb.build()
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
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service("/api", HelloService())
                    .build()
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
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import java.time.Duration

            fun main() {
                Server.builder()
                    .requestTimeout(Duration.ofSeconds(30))
                    .service("/api", HelloService())
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

    fun testCollectGrpcServiceRegistrationWithBuild() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.grpc.GrpcService

            fun main() {
                Server.builder()
                    .service("/grpc", GrpcService.builder(HelloGrpcService()).build())
                    .build()
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
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.docs.DocService

            fun main() {
                Server.builder()
                    .service("/docs", DocService.builder().build())
                    .build()
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
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service("/api", MissingService())
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val serviceRoute = routes.firstOrNull { it.path == "/api" }
        assertNotNull(serviceRoute)
        assertTrue(serviceRoute!!.targetUnresolved)
    }

    fun testCollectServiceRegistrationWithNamedArgumentsReversedOrder() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service(service = HelloService(), path = "/api")
                    .build()
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

    fun testCollectServiceRegistrationInApplyBlock() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().apply {
                    service("/api", HelloService())
                }.build()
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

    fun testCollectServiceRegistrationInAlsoBlockWithExplicitReceiver() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().also {
                    it.service("/api", HelloService())
                }.build()
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

    fun testCollectServiceRegistrationWithConstValPath() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            private const val API_PATH = "/api"

            fun main() {
                Server.builder()
                    .service(API_PATH, HelloService())
                    .build()
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
    }

    fun testCollectServiceUnderRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .serviceUnder("/v1", HelloService())
                    .build()
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

        val serviceRoute = routes.firstOrNull { it.routeMatch == RouteMatch.SERVICE_UNDER }
        assertNotNull(serviceRoute)
        assertEquals("/v1", serviceRoute!!.path)
        assertEquals("example.HelloService", serviceRoute.target)
    }

    fun testCollectServiceUnderRegistrationWithPathPrefixNamedArgument() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .serviceUnder(service = HelloService(), pathPrefix = "/v1")
                    .build()
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

        val serviceRoute = routes.firstOrNull { it.routeMatch == RouteMatch.SERVICE_UNDER }
        assertNotNull(serviceRoute)
        assertEquals("/v1", serviceRoute!!.path)
        assertEquals("example.HelloService", serviceRoute.target)
    }

    fun testCollectServiceRegistrationFromFullyQualifiedServerBuilder() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            fun main() {
                com.linecorp.armeria.server.Server.builder()
                    .service("/api", Any())
                    .build()
            }
            """.trimIndent(),
        )

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals(
            "Server.builder().service(\"/api\", …)",
            ArmeriaRouteDetailFormatter.registrationSummary(serviceRoute!!),
        )
    }

    fun testResolvedConstructorTargetIsNotMarkedUnresolved() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service("/api", HelloService())
                    .build()
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

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute!!.target)
        assertFalse(serviceRoute.targetUnresolved)
    }

    fun testCollectServiceRegistrationFromNullableServerBuilderVariable() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.ServerBuilder

            fun main() {
                val sb: ServerBuilder? = Server.builder()
                sb!!.service("/api", HelloService())
                sb!!.build()
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

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute!!.target)
    }

    fun testCollectServiceRegistrationFromAnnotatedServerBuilderVariable() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.ServerBuilder

            annotation class Ann

            fun main() {
                val sb: @Ann ServerBuilder? = Server.builder()
                sb!!.service("/api", HelloService())
                sb!!.build()
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

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute!!.target)
        assertFalse(serviceRoute.targetUnresolved)
    }

    fun testCollectServiceRegistrationWithKotlinDefinedServiceClass() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            class HelloService
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service("/api", HelloService())
                    .build()
            }
            """.trimIndent(),
        )

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute!!.target)
        assertFalse(serviceRoute.targetUnresolved)
    }

    fun testCollectServiceRegistrationWithJavaStaticFinalPath() {
        myFixture.addClass(
            """
            package example;

            public final class Routes {
                public static final String API_PATH = "/api";
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service(Routes.API_PATH, HelloService())
                    .build()
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

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
    }

    fun testCollectServiceRegistrationFromServerBuilderExtensionFunction() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.ServerBuilder

            fun ServerBuilder.configure() {
                service("/api", HelloService())
            }

            fun main() {
                Server.builder()
                    .configure()
                    .build()
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

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute!!.target)
    }

    fun testCollectServiceRegistrationFromServerBuilderTypeAlias() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.ServerBuilder

            typealias SB = ServerBuilder

            fun main() {
                val sb: SB = Server.builder()
                sb.service("/api", HelloService())
                sb.build()
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

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute!!.target)
    }

    fun testCollectServiceRegistrationFromParenthesizedServerBuilderReceiver() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.ServerBuilder

            fun main() {
                val sb: ServerBuilder = Server.builder()
                (sb).service("/api", HelloService())
                sb.build()
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

        val serviceRoute = ArmeriaRouteCollector.collect(project)
            .firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute!!.target)
    }
}
