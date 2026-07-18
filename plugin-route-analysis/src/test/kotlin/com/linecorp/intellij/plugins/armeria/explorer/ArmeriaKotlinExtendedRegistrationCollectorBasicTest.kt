package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaKotlinExtendedRegistrationCollectorBasicTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerKotlinExtendedRegistrationCollectorStubs()
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
        assertEquals(PathType.EXACT, fluentRoute!!.pathType)
        assertEquals("/api/items", fluentRoute.path)
        assertEquals("GET", fluentRoute.httpMethod)
    }

    fun testCollectKotlinFileServiceFromConstValPath() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import java.io.File

            private const val FILES_PATH = "/files/"

            fun main() {
                Server.builder()
                    .fileService(FILES_PATH, File("/tmp"))
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val fileRoute = routes.firstOrNull { it.routeMatch == RouteMatch.FILE_SERVICE }
        assertNotNull(fileRoute)
        assertEquals("/files/", fileRoute!!.path)
    }
}
