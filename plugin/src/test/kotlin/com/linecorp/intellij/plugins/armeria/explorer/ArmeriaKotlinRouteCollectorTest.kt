package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaKotlinRouteCollectorTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerArmeriaStubs()
    }

    fun testCollectAnnotatedRouteFromKotlinLightMethod() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"
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

    fun testCollectAnnotatedServiceRegistration() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"
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
                    .annotatedService(HelloService())
                    .build()
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
            "PrefixedService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.PathPrefix

            @PathPrefix("/v1")
            class PrefixedService {
                @Get("/items")
                fun items(): String = "items"
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertEquals(1, routes.size)
        assertEquals("/v1/items", routes.single().path)
    }

    fun testNoFalsePositiveForUnrelatedServiceCall() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .build()
                Client.service("/oops", Any())
            }

            object Client {
                fun service(path: String, handler: Any) {}
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
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

    fun testCollectProgrammaticDecoratorOnKotlinServiceRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.cors.CorsService
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder()
                    .decorator(LoggingService::class.java)
                    .decorator(CorsService::class.java)
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

        val routes = ArmeriaRouteCollector.collect(project)

        val serviceRoute = routes.firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals(listOf("CORS", "Logging"), serviceRoute!!.decorators)
    }

    fun testCollectProgrammaticDecoratorInApplyBlock() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder().apply {
                    decorator(LoggingService::class.java)
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
        assertEquals(listOf("Logging"), serviceRoute!!.decorators)
    }

    fun testCollectProgrammaticDecoratorOnKotlinBuilderVariable() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                val sb = Server.builder()
                sb.decorator(LoggingService::class.java)
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

        val routes = ArmeriaRouteCollector.collect(project)

        val serviceRoute = routes.firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals(listOf("Logging"), serviceRoute!!.decorators)
    }

    fun testCollectProgrammaticPathScopedDecoratorOnKotlinServiceRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder()
                    .decorator("/api/**", LoggingService::class.java)
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

        val routes = ArmeriaRouteCollector.collect(project)

        val serviceRoute = routes.firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals(listOf("Logging"), serviceRoute!!.decorators)
    }

    fun testCollectPathScopedDecoratorFiltersByRoute() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder()
                    .decorator("/api/**", LoggingService::class.java)
                    .service("/api", HelloService())
                    .service("/other", HelloService())
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

        val apiRoute = routes.firstOrNull { it.path == "/api" }
        val otherRoute = routes.firstOrNull { it.path == "/other" }
        assertNotNull(apiRoute)
        assertNotNull(otherRoute)
        assertEquals(listOf("Logging"), apiRoute!!.decorators)
        assertEquals(emptyList<String>(), otherRoute!!.decorators)
    }

    fun testCollectProgrammaticDecoratorInAlsoBlock() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder().also {
                    it.decorator(LoggingService::class.java)
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
        assertEquals(listOf("Logging"), serviceRoute!!.decorators)
    }

    fun testCollectProgrammaticDecoratorInApplyBlockChainedAfterApply() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder().apply {
                    decorator(LoggingService::class.java)
                }.service("/api", HelloService())
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
        assertEquals(listOf("Logging"), serviceRoute!!.decorators)
    }

    fun testCollectProgrammaticDecoratorInChainedApplyBlocks() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder()
                    .apply { decorator(LoggingService::class.java) }
                    .apply { service("/api", HelloService()) }
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
        assertEquals(listOf("Logging"), serviceRoute!!.decorators)
    }

    fun testCollectPathScopedDecoratorWithConstValPathPattern() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            private const val API_PATH = "/api/**"

            fun main() {
                Server.builder()
                    .decorator(API_PATH, LoggingService::class.java)
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

        val routes = ArmeriaRouteCollector.collect(project)

        val serviceRoute = routes.firstOrNull { it.path == "/api" && it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals(listOf("Logging"), serviceRoute!!.decorators)
    }

    fun testCollectProgrammaticDecoratorInApplyBlockStopsAfterRegistrationStatement() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.cors.CorsService
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder().apply {
                    decorator(LoggingService::class.java)
                    val x = service("/api", HelloService())
                    decorator(CorsService::class.java)
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
        assertEquals(listOf("Logging"), serviceRoute!!.decorators)
    }

    fun testCollectProgrammaticDecoratorInApplyBlockStopsWithinRegistrationStatement() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.cors.CorsService
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder().apply {
                    decorator(LoggingService::class.java)
                    service("/api", HelloService()).also {
                        decorator(CorsService::class.java)
                    }
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
        assertEquals(listOf("Logging"), serviceRoute!!.decorators)
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

    fun testNoFalsePositiveForUnqualifiedServiceCallInAlsoBlock() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().also {
                    service("/oops", Any())
                }.build()
            }

            private fun service(path: String, handler: Any) {}
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
    }

    fun testNoFalsePositiveForUnqualifiedServiceCallInLetBlock() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().let {
                    service("/oops", Any())
                }.build()
            }

            private fun service(path: String, handler: Any) {}
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
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

    fun testCollectAnnotatedServiceWithPathPrefixNamedArgument() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"
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
                    .annotatedService(service = HelloService(), pathPrefix = "/v1")
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val registrationRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ANNOTATED_SERVICE }
        assertNotNull(registrationRoute)
        assertEquals("/v1", registrationRoute!!.path)
        assertTrue(registrationRoute.annotatedServiceHasPathPrefix)
    }

    fun testCollectAnnotatedServiceWithPathPrefix() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"
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
                    .annotatedService("/v1", HelloService())
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val registrationRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ANNOTATED_SERVICE }
        assertNotNull(registrationRoute)
        assertEquals("/v1", registrationRoute!!.path)
        assertTrue(registrationRoute.annotatedServiceHasPathPrefix)
    }

    fun testNoFalsePositiveForMisleadingServerBuilderTypeName() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().build()
                val helper = MyServerBuilderHelper()
                helper.service("/oops", Any())
            }

            class MyServerBuilderHelper {
                fun service(path: String, handler: Any) {}
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
    }

    fun testNoFalsePositiveForMyServerBuilderCall() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().build()
                MyServer.builder()
                    .service("/oops", Any())
                    .build()
            }

            object MyServer {
                fun builder(): FakeBuilder = FakeBuilder()
            }

            class FakeBuilder {
                fun service(path: String, handler: Any): FakeBuilder = this
                fun build() {}
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
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

    fun testNoFalsePositiveForServerBuilderNamedNonArmeriaVariable() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().build()
                val serverBuilderHelper = FakeBuilder()
                serverBuilderHelper.service("/oops", Any())
            }

            class FakeBuilder {
                fun service(path: String, handler: Any) {}
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
    }

    fun testCollectUnresolvedParenthesizedNewExpressionTarget() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service("/api", (MissingService()))
                    .build()
            }
            """.trimIndent(),
        )

        val serviceRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.path == "/api" }
        assertNotNull(serviceRoute)
        assertTrue(serviceRoute!!.targetUnresolved)
    }

    fun testCollectUnresolvedFactoryMethodTarget() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service("/api", createMissingService())
                    .build()
            }

            private fun createMissingService(): Any = MissingService()
            """.trimIndent(),
        )

        val serviceRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.path == "/api" }
        assertNotNull(serviceRoute)
        assertTrue(serviceRoute!!.targetUnresolved)
    }

    fun testCollectUnresolvedGrpcServiceBuilderTarget() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.grpc.GrpcService

            fun main() {
                Server.builder()
                    .service("/grpc", GrpcService.builder(MissingService()).build())
                    .build()
            }
            """.trimIndent(),
        )

        val serviceRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.path == "/grpc" }
        assertNotNull(serviceRoute)
        assertTrue(serviceRoute!!.targetUnresolved)
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

    fun testNoFalsePositiveForNonArmeriaQualifiedServerBuilder() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder().build()
                com.foo.Server.builder()
                    .service("/oops", Any())
                    .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.foo;

            public final class Server {
                public static FakeBuilder builder() {
                    return null;
                }
            }

            class FakeBuilder {
                public FakeBuilder service(String path, Object handler) {
                    return this;
                }

                public void build() {}
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertNull(routes.firstOrNull { it.path == "/oops" })
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

                public ServerBuilder serviceUnder(String pathPrefix, Object service) {
                    return this;
                }

                public ServerBuilder annotatedService(Object service) {
                    return this;
                }

                public ServerBuilder annotatedService(String pathPrefix, Object service) {
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
