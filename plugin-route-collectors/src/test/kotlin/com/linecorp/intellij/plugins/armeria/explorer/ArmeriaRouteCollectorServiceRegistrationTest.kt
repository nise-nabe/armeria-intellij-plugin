package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.DelegationKind
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull as kotlinAssertNotNull

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
        kotlinAssertNotNull(serviceRoute)
        assertEquals("example.HelloService", serviceRoute.target)
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
        kotlinAssertNotNull(serviceRoute)
        assertEquals("/api", serviceRoute.path)
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
        kotlinAssertNotNull(grpcRoute)
        assertEquals("example.HelloGrpcService", grpcRoute.target)
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
        kotlinAssertNotNull(docRoute)
        assertEquals("com.linecorp.armeria.server.docs.DocService", docRoute.target)
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
        kotlinAssertNotNull(serviceRoute)
        assertTrue(serviceRoute.targetUnresolved)
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
        kotlinAssertNotNull(serviceRoute)
        assertTrue(serviceRoute.targetUnresolved)
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
        kotlinAssertNotNull(serviceRoute)
        assertTrue(serviceRoute.targetUnresolved)
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

    fun testCollectDocServiceRegistrationWithConstructor() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.docs.DocService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/docs", new DocService())
                        .build();
                }
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
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.docs.DocService;

            public class Main {
                public static void main(String[] args) {
                    DocService docs = new DocService();
                    Server.builder()
                        .service("/docs", docs)
                        .build();
                }
            }
            """.trimIndent(),
        )

        val docRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.isDocService }
        kotlinAssertNotNull(docRoute)
        assertEquals(RouteMatch.NON_HTTP, docRoute.routeMatch)
    }

    fun testCollectPrometheusExpositionService() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.metric.PrometheusExpositionService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/metrics", PrometheusExpositionService.of(null))
                        .build();
                }
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
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.grpc.GrpcService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/grpc", GrpcService.builder().addService(new HelloGrpcService()).build())
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
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.jetty.JettyService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/app", JettyService.of(null))
                        .build();
                }
            }
            """.trimIndent(),
        )

        val servletRoute = ArmeriaRouteCollector.collect(project).single { it.path == "/app" }
        assertEquals(RouteMatch.SERVICE, servletRoute.routeMatch)
        assertEquals(DelegationKind.SERVLET, servletRoute.delegationKind)
        assertEquals(RouteProtocol.HTTP.presentableName(), servletRoute.protocol)
    }

    fun testCollectFileServiceViaServiceRegistration() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.file.FileService;
            import java.io.File;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/files", FileService.of(new File("/tmp")))
                        .build();
                }
            }
            """.trimIndent(),
        )

        val fileRoute = ArmeriaRouteCollector.collect(project).single { it.path == "/files" }
        assertEquals(RouteMatch.FILE_SERVICE, fileRoute.routeMatch)
        assertEquals(RouteProtocol.HTTP.presentableName(), fileRoute.protocol)
        assertEquals("com.linecorp.armeria.server.file.FileService", fileRoute.target)
    }

    fun testCollectDocServiceRegistrationFromHttpServiceVariable() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.HttpService;
            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.docs.DocService;

            public class Main {
                public static void main(String[] args) {
                    HttpService docs = new DocService();
                    Server.builder()
                        .service("/docs", docs)
                        .build();
                }
            }
            """.trimIndent(),
        )

        val docRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.isDocService }
        kotlinAssertNotNull(docRoute)
        assertEquals(RouteMatch.NON_HTTP, docRoute.routeMatch)
    }

    fun testCollectDocServiceRegistrationWithDecorate() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;
            import com.linecorp.armeria.server.docs.DocService;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/docs", new DocService().decorate(null))
                        .build();
                }
            }
            """.trimIndent(),
        )

        val docRoute = ArmeriaRouteCollector.collect(project).firstOrNull { it.isDocService }
        kotlinAssertNotNull(docRoute)
        assertEquals(RouteMatch.NON_HTTP, docRoute.routeMatch)
    }

    fun testUserFileServiceIsNotArmeriaFileService() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    Server.builder()
                        .service("/files", new FileService())
                        .build();
                }
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
        assertEquals("example.FileService", fileRoute.target)
    }

    fun testUserFileServiceVariableIsNotArmeriaFileService() {
        myFixture.configureByText(
            "Main.java",
            """
            package example;

            import com.linecorp.armeria.server.Server;

            public class Main {
                public static void main(String[] args) {
                    FileService files = new FileService();
                    Server.builder()
                        .service("/files", files)
                        .build();
                }
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
}
