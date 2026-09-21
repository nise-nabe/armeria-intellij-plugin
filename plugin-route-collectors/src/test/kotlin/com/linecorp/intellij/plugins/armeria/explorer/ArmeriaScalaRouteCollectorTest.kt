package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.GrpcRouteHint
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull as kotlinAssertNotNull

class ArmeriaScalaRouteCollectorTest : ArmeriaFixtureTestBase() {
    fun testCollectServiceRegistrationFromScalaBuilderChain() {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.server.Server

            object Main {
              Server.builder()
                .service("/api", new HelloService())
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
        assertEquals("HelloService", serviceRoute.target)
        assertFalse(serviceRoute.targetUnresolved)
        val sourceOffset = kotlinAssertNotNull(serviceRoute.sourceOffset)
        assertTrue(sourceOffset > 0)
        assertFalse(serviceRoute.resolveSourceHint().endsWith(":1"))
    }

    fun testCollectMultipleServiceRegistrationsFromSameScalaFile() {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.server.Server

            object Main {
              Server.builder()
                .service("/api", new HelloService())
                .service("/admin", new AdminService())
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
        myFixture.addClass(
            """
            package example;

            public class AdminService {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val serviceRoutes = routes.filter { it.routeMatch == RouteMatch.SERVICE }
        assertEquals(2, serviceRoutes.size)
        val byPath = serviceRoutes.associateBy { it.path }
        assertEquals("HelloService", byPath.getValue("/api").target)
        assertEquals("AdminService", byPath.getValue("/admin").target)
        assertFalse(byPath.getValue("/api").sourceOffset == byPath.getValue("/admin").sourceOffset)
    }

    fun testCollectAnnotatedServiceRegistrationFromScala() {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.server.Server

            object Main {
              Server.builder()
                .annotatedService("/prefix", new AnnotatedService())
                .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class AnnotatedService {
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val route = routes.firstOrNull { it.path == "/prefix" && it.routeMatch == RouteMatch.ANNOTATED_SERVICE }
        kotlinAssertNotNull(route)
        assertEquals("AnnotatedService", route.target)
    }

    fun testCollectScalaGrpcServiceUnframedAndReflectionHints() {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.grpc.GrpcService
            import io.grpc.protobuf.services.ProtoReflectionService

            object Main {
              Server.builder()
                .service("/grpc", GrpcService.builder()
                  .addService(new HelloGrpcService())
                  .enableUnframedRequests(true)
                  .addService(ProtoReflectionService.newInstance())
                  .build())
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

        val grpcRoute = ArmeriaRouteCollector.collect(project).single { it.path == "/grpc" }

        assertTrue(grpcRoute.contentHints.contains(GrpcRouteHint.UNFRAMED))
        assertTrue(grpcRoute.contentHints.contains(GrpcRouteHint.REFLECTION))
    }

    fun testCollectScalaGrpcServiceEnableUnframedRequestsFalseIsNotUnframed() {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.grpc.GrpcService

            object Main {
              Server.builder()
                .service("/grpc", GrpcService.builder()
                  .addService(new HelloGrpcService())
                  .enableUnframedRequests(false)
                  .build())
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

        val grpcRoute = ArmeriaRouteCollector.collect(project).single { it.path == "/grpc" }

        assertFalse(grpcRoute.contentHints.contains(GrpcRouteHint.UNFRAMED))
        assertFalse(grpcRoute.contentHints.contains(GrpcRouteHint.REFLECTION))
    }

    fun testCollectScalaGrpcServiceDoesNotHintUnrelatedLookalikes() {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.grpc.GrpcService

            object Main {
              // .enableUnframedRequests(true) inside a comment must not count
              Server.builder()
                .service("/grpc", GrpcService.builder()
                  .addService(new MyProtoReflectionService())
                  .build())
                .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class MyProtoReflectionService {
            }
            """.trimIndent(),
        )

        val grpcRoute = ArmeriaRouteCollector.collect(project).single { it.path == "/grpc" }

        assertFalse(grpcRoute.contentHints.contains(GrpcRouteHint.UNFRAMED))
        assertFalse(grpcRoute.contentHints.contains(GrpcRouteHint.REFLECTION))
    }

    fun testCollectScalaGrpcServiceIgnoresOptionTokensInsideStringLiterals() {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.grpc.GrpcService

            object Main {
              Server.builder()
                .service("/grpc", GrpcService.builder()
                  .addService(new HelloGrpcService("call .enableUnframedRequests(true) and ProtoReflectionService"))
                  .build())
                .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class HelloGrpcService {
                public HelloGrpcService(String name) {
                }
            }
            """.trimIndent(),
        )

        val grpcRoute = ArmeriaRouteCollector.collect(project).single { it.path == "/grpc" }

        assertFalse(grpcRoute.contentHints.contains(GrpcRouteHint.UNFRAMED))
        assertFalse(grpcRoute.contentHints.contains(GrpcRouteHint.REFLECTION))
    }

    fun testCollectScalaGrpcServiceHintsSurviveParensInsideLiterals() {
        myFixture.configureByText(
            "Main.scala",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.grpc.GrpcService

            object Main {
              Server.builder()
                .service("/grpc", GrpcService.builder()
                  .addService(new HelloGrpcService(")"))
                  .enableUnframedRequests(true)
                  .build())
                .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example;

            public class HelloGrpcService {
                public HelloGrpcService(String name) {
                }
            }
            """.trimIndent(),
        )

        val grpcRoute = ArmeriaRouteCollector.collect(project).single { it.path == "/grpc" }

        assertTrue(grpcRoute.contentHints.contains(GrpcRouteHint.UNFRAMED))
    }
}
