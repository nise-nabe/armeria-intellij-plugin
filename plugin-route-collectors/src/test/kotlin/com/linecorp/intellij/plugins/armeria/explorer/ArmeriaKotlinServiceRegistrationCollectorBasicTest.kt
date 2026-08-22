package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.DelegationKind
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull as kotlinAssertNotNull

class ArmeriaKotlinServiceRegistrationCollectorBasicTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerKotlinRouteCollectorStubs()
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
        kotlinAssertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute.target)
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
        kotlinAssertNotNull(serviceRoute)
        assertEquals("/api", serviceRoute.path)
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
        kotlinAssertNotNull(grpcRoute)
        assertEquals("example.HelloGrpcService", grpcRoute.target)
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
        kotlinAssertNotNull(docRoute)
        assertEquals("com.linecorp.armeria.server.docs.DocService", docRoute.target)
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
        kotlinAssertNotNull(serviceRoute)
        assertTrue(serviceRoute.targetUnresolved)
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
        kotlinAssertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute.target)
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
        kotlinAssertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute.target)
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
        kotlinAssertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute.target)
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
        kotlinAssertNotNull(serviceRoute)
    }

    fun testCollectDocServiceRegistrationWithConstructor() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.docs.DocService

            fun main() {
                Server.builder()
                    .service("/docs", DocService())
                    .build()
            }
            """.trimIndent(),
        )

        val docRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.isDocService }
        kotlinAssertNotNull(docRoute)
        assertEquals("/docs", docRoute.path)
        assertEquals("com.linecorp.armeria.server.docs.DocService", docRoute.target)
        assertEquals(RouteMatch.NON_HTTP, docRoute.routeMatch)
        assertEquals(RouteProtocol.DOC_SERVICE.presentableName(), docRoute.protocol)
    }

    fun testCollectDocServiceRegistrationFromVariable() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.docs.DocService

            fun main() {
                val docs = DocService()
                Server.builder()
                    .service("/docs", docs)
                    .build()
            }
            """.trimIndent(),
        )

        val docRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.isDocService }
        kotlinAssertNotNull(docRoute)
        assertEquals(RouteMatch.NON_HTTP, docRoute.routeMatch)
    }

    fun testCollectPrometheusExpositionService() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.metric.PrometheusExpositionService

            fun main() {
                Server.builder()
                    .service("/metrics", PrometheusExpositionService.of(null))
                    .build()
            }
            """.trimIndent(),
        )

        val metricsRoute = ArmeriaRouteCollector.collect(project).single { it.path == "/metrics" }
        assertEquals(RouteMatch.SERVICE, metricsRoute.routeMatch)
        assertEquals(RouteProtocol.HTTP.presentableName(), metricsRoute.protocol)
        assertFalse(metricsRoute.isDocService)
        assertEquals(
            "com.linecorp.armeria.server.metric.PrometheusExpositionService",
            metricsRoute.target,
        )
    }

    fun testCollectGrpcServiceRegistrationWithAddService() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.grpc.GrpcService

            fun main() {
                Server.builder()
                    .service("/grpc", GrpcService.builder().addService(HelloGrpcService()).build())
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

        val grpcRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.routeMatch == RouteMatch.NON_HTTP }
        kotlinAssertNotNull(grpcRoute)
        assertEquals("/grpc", grpcRoute.path)
        assertEquals(RouteProtocol.GRPC.presentableName(), grpcRoute.protocol)
        assertFalse(grpcRoute.target.equals("addService", ignoreCase = true))
        assertFalse(grpcRoute.target.equals("build", ignoreCase = true))
    }

    fun testCollectJettyServiceRegistration() {
        registerServletServiceStubs()
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.jetty.JettyService

            fun main() {
                Server.builder()
                    .service("/app", JettyService.of(null))
                    .build()
            }
            """.trimIndent(),
        )

        val servletRoute = ArmeriaRouteCollector.collect(project).single { it.path == "/app" }
        assertEquals(RouteMatch.SERVICE, servletRoute.routeMatch)
        assertEquals(DelegationKind.SERVLET, servletRoute.delegationKind)
        assertEquals(RouteProtocol.HTTP.presentableName(), servletRoute.protocol)
    }

    fun testCollectKotlinWebSocketServiceViaServiceRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.websocket.WebSocketService

            fun main() {
                Server.builder()
                    .service("/chat", WebSocketService.of(null))
                    .build()
            }
            """.trimIndent(),
        )

        val websocketRoute = ArmeriaRouteCollector.collect(project).single { it.path == "/chat" }
        assertEquals(RouteMatch.SERVICE, websocketRoute.routeMatch)
        assertEquals(RouteProtocol.WEBSOCKET.presentableName(), websocketRoute.protocol)
        assertEquals("", websocketRoute.httpMethod)
        assertFalse(websocketRoute.excludeFromDuplicateIndex)
    }

    fun testCollectKotlinHealthCheckServiceViaServiceRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.healthcheck.HealthCheckService

            fun main() {
                Server.builder()
                    .service("/health", HealthCheckService.of())
                    .build()
            }
            """.trimIndent(),
        )

        val healthRoute = ArmeriaRouteCollector.collect(project).single { it.path == "/health" }
        assertEquals(RouteMatch.HEALTH_CHECK, healthRoute.routeMatch)
        assertEquals(RouteProtocol.HEALTH_CHECK.presentableName(), healthRoute.protocol)
        assertEquals("GET", healthRoute.httpMethod)
    }

    fun testCollectKotlinServerSentEventsViaServiceRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.streaming.ServerSentEvents

            fun main() {
                Server.builder()
                    .service("/events", ServerSentEvents.fromPublisher(null))
                    .build()
            }
            """.trimIndent(),
        )

        val sseRoute = ArmeriaRouteCollector.collect(project).single { it.path == "/events" }
        assertEquals(RouteMatch.SERVICE, sseRoute.routeMatch)
        assertEquals(RouteProtocol.SSE.presentableName(), sseRoute.protocol)
        assertEquals("GET", sseRoute.httpMethod)
        assertEquals(
            listOf(message("route.explorer.hint.produces", "text/event-stream")),
            sseRoute.contentHints,
        )
    }

    fun testCollectDocServiceRegistrationFromHttpServiceVariable() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.HttpService
            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.docs.DocService

            fun main() {
                val docs: HttpService = DocService()
                Server.builder()
                    .service("/docs", docs)
                    .build()
            }
            """.trimIndent(),
        )

        val docRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.isDocService }
        kotlinAssertNotNull(docRoute)
        assertEquals(RouteMatch.NON_HTTP, docRoute.routeMatch)
    }

    fun testCollectDocServiceRegistrationWithDecorate() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.docs.DocService
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder()
                    .service("/docs", DocService().decorate(LoggingService.newDecorator()))
                    .build()
            }
            """.trimIndent(),
        )

        val docRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.isDocService }
        kotlinAssertNotNull(docRoute)
        assertEquals(RouteMatch.NON_HTTP, docRoute.routeMatch)
    }

    fun testUserFileServiceTypedPropertyIsNotArmeriaFileService() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                val files: FileService = FileService()
                Server.builder()
                    .service("/files", files)
                    .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class FileService {
            }
            """.trimIndent(),
        )

        val fileRoute = ArmeriaRouteCollector.collect(project).single { it.path == "/files" }
        assertEquals(RouteMatch.SERVICE, fileRoute.routeMatch)
        assertFalse(fileRoute.excludeFromDuplicateIndex)
    }

    fun testUserWebSocketServiceTypedPropertyIsNotArmeriaWebSocketService() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                val chat: WebSocketService = WebSocketService()
                Server.builder()
                    .service("/chat", chat)
                    .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class WebSocketService {
            }
            """.trimIndent(),
        )

        val websocketRoute = ArmeriaRouteCollector.collect(project).single { it.path == "/chat" }
        assertEquals(RouteMatch.SERVICE, websocketRoute.routeMatch)
        assertEquals(RouteProtocol.HTTP.presentableName(), websocketRoute.protocol)
        assertFalse(websocketRoute.excludeFromDuplicateIndex)
    }

    fun testCollectDocServiceRegistrationFromCast() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.HttpService
            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.docs.DocService

            fun main() {
                val docs = DocService()
                Server.builder()
                    .service("/docs", docs as HttpService)
                    .build()
            }
            """.trimIndent(),
        )

        val docRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.isDocService }
        kotlinAssertNotNull(docRoute)
        assertEquals(RouteMatch.NON_HTTP, docRoute.routeMatch)
    }
}
