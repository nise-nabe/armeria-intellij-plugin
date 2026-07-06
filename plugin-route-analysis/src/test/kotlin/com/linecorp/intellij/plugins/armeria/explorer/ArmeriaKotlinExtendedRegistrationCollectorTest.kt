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

    fun testCollectKotlinChainedVirtualHostDoesNotAnnotatePrecedingServiceRoute() {
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
        assertEquals("", serviceRoute.virtualHostName)
    }

    fun testCollectKotlinVirtualHostThenServiceAnnotatesServiceRoute() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .virtualHost("api.example.com")
                    .service("/api", ApiService())
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

    fun testCollectKotlinNestedVirtualHostLambdaUsesInnerHostname() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .virtualHost("outer.example.com") { outer ->
                        outer.virtualHost("inner.example.com") { inner ->
                            inner.service("/api", ApiService())
                        }
                    }
                    .build()
            }
            """.trimIndent(),
        )
        myFixture.addClass("package example; public class ApiService {}")

        val routes = ArmeriaRouteCollector.collect(project)
        val serviceRoute = routes.firstOrNull { it.routeMatch == RouteMatch.SERVICE }
        assertNotNull(serviceRoute)
        assertEquals("/api", serviceRoute!!.path)
        assertEquals("inner.example.com", serviceRoute.virtualHostName)
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

    fun testCollectKotlinHealthCheckRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .healthCheckService()
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val healthRoute = routes.firstOrNull { it.routeMatch == RouteMatch.HEALTH_CHECK }
        assertNotNull(healthRoute)
        assertEquals("/internal/healthcheck", healthRoute!!.path)
    }

    fun testCollectKotlinVirtualHostRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .virtualHost("api.example.com")
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val virtualHostRoute = routes.firstOrNull { it.routeMatch == RouteMatch.VIRTUAL_HOST }
        assertNotNull(virtualHostRoute)
        assertEquals("api.example.com", virtualHostRoute!!.virtualHostName)
    }

    fun testCollectKotlinRouteDecoratorRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder()
                    .routeDecorator()
                    .path("/decorated/**")
                    .build(LoggingService.newDecorator())
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertNotNull(routes.firstOrNull { it.routeMatch == RouteMatch.ROUTE_DECORATOR })
    }

    fun testIgnoreNonArmeriaKotlinFluentBuildCalls() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            fun main() {
                OtherBuilder.builder()
                    .route()
                    .post("/nope")
                    .build(Any())
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testCollectKotlinVirtualHostLambdaAnnotatesFileService() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import java.io.File

            fun main() {
                Server.builder()
                    .virtualHost("api.example.com") { sb ->
                        sb.fileService("/files/", File("/tmp"))
                    }
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val fileRoute = routes.firstOrNull { it.routeMatch == RouteMatch.FILE_SERVICE }
        assertNotNull(fileRoute)
        assertEquals("/files/", fileRoute!!.path)
        assertEquals("api.example.com", fileRoute.virtualHostName)
    }

    fun testCollectKotlinFluentRoutePathPrefix() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .route()
                    .pathPrefix("/api")
                    .get("/items")
                    .build(Any())
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val fluentRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ROUTE_FLUENT }
        assertNotNull(fluentRoute)
        assertEquals(PathType.PREFIX, fluentRoute!!.pathType)
        assertEquals("/api", fluentRoute.path)
        assertEquals("GET", fluentRoute.httpMethod)
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

                public ServerBuilder virtualHost(String hostname, java.util.function.Consumer<ServerBuilder> customizer) {
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

                public ServerBuilder get(String path) {
                    return this;
                }

                public ServerBuilder pathPrefix(String pathPrefix) {
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
        myFixture.addClass(
            """
            package example;

            public final class OtherBuilder {
                public static OtherBuilder builder() {
                    return null;
                }

                public OtherBuilder route() {
                    return this;
                }

                public OtherBuilder post(String path) {
                    return this;
                }

                public OtherBuilder build(Object handler) {
                    return this;
                }

                public Object build() {
                    return null;
                }
            }
            """.trimIndent(),
        )
    }
}
