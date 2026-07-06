package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaKotlinExtendedRegistrationCollectorTest : ArmeriaFixtureTestBase() {

    override fun registerArmeriaStubs() {
        registerKotlinExtendedRegistrationStubs()
    }

    fun testCollectKotlinFileServiceRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import java.io.File

            fun main() {
                Server.builder()
                    .fileService("/files/", File("/tmp"))
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val fileRoute = routes.firstOrNull { it.routeMatch == RouteMatch.FILE_SERVICE }
        assertNotNull(fileRoute)
        assertEquals("/files/", fileRoute!!.path)
    }

    fun testCollectKotlinFluentRouteRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .route()
                    .post("/api/items")
                    .build(Any())
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val fluentRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ROUTE_FLUENT }
        assertNotNull(fluentRoute)
        assertEquals("POST", fluentRoute!!.httpMethod)
        assertEquals("/api/items", fluentRoute.path)
    }

    fun testCollectKotlinChainedVirtualHostAnnotatesServiceRoute() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .service("/api", ApiService())
                    .virtualHost("api.example.com")
                    .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class ApiService {}")

        val routes = ArmeriaRouteCollector.collect(project)
        val serviceRoute = routes.firstOrNull { it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("/api", serviceRoute!!.path)
        assertEquals("api.example.com", serviceRoute.virtualHostName)
    }

    fun testCollectKotlinDecoratorUnderRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder()
                    .decoratorUnder("/public", LoggingService.newDecorator())
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val decoratorRoute = routes.firstOrNull { it.routeMatch == RouteMatch.DECORATOR_UNDER }
        assertNotNull(decoratorRoute)
        assertEquals("/public", decoratorRoute!!.path)
    }

    fun testCollectKotlinWithRouteRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.RouteBuilder
            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .withRoute { route: RouteBuilder ->
                        route.post("/wrapped").build(Any())
                    }
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val fluentRoutes = routes.filter { it.routeMatch == RouteMatch.ROUTE_FLUENT }
        assertEquals(1, fluentRoutes.size)
        assertEquals("POST", fluentRoutes.single().httpMethod)
        assertEquals("/wrapped", fluentRoutes.single().path)
    }

    fun testCollectKotlinWithRouteDoesNotBurnDedupKeyOnInvalidLambda() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.RouteBuilder
            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .withRoute { _: RouteBuilder -> null }
                    .withRoute { route: RouteBuilder ->
                        route.post("/wrapped").build(Any())
                    }
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val fluentRoutes = routes.filter { it.routeMatch == RouteMatch.ROUTE_FLUENT }
        assertEquals(1, fluentRoutes.size)
        assertEquals("/wrapped", fluentRoutes.single().path)
    }

    private fun registerKotlinExtendedRegistrationStubs() {
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

                public ServerBuilder fileService(String path, java.io.File root) {
                    return this;
                }

                public ServerBuilder healthCheckService() {
                    return this;
                }

                public ServerBuilder virtualHost(String hostname) {
                    return this;
                }

                public ServerBuilder routeDecorator() {
                    return this;
                }

                public ServerBuilder path(String pathPattern) {
                    return this;
                }

                public ServerBuilder withRoute(java.util.function.Function<RouteBuilder, RouteBuilder> fn) {
                    return this;
                }

                public ServerBuilder route() {
                    return this;
                }

                public ServerBuilder post(String path) {
                    return this;
                }

                public ServerBuilder build(Object handler) {
                    return this;
                }

                public ServerBuilder decoratorUnder(String path, Object decorator) {
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
            package com.linecorp.armeria.server;

            public final class RouteBuilder {
                public RouteBuilder post(String path) {
                    return this;
                }

                public RouteBuilder build(Object handler) {
                    return this;
                }
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.logging;

            public final class LoggingService {
                public static Object newDecorator() {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }
}
