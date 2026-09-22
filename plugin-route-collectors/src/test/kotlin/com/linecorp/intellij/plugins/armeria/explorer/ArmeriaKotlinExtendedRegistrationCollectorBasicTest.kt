package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.model.FileServiceRootKind
import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import com.linecorp.intellij.plugins.armeria.test.assertRoute
import com.linecorp.intellij.plugins.armeria.test.singleRoute
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaKotlinExtendedRegistrationCollectorBasicTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerKotlinExtendedRegistrationCollectorStubs()
    }

    fun testCollectKotlinFileServiceRegistration() {
        configureFixture("extendedRegistration/kotlin/basic/fileService/Main.kt")
        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.FILE_SERVICE, path = "/files/")
            .also { route ->
                assertEquals(FileServiceRootKind.FILE_SYSTEM, route.fileServiceRoot?.kind)
                assertEquals("/tmp", route.fileServiceRoot?.path)
            }
    }

    fun testCollectKotlinFileServiceWithPathsGetRoot() {
        configureFixture("extendedRegistration/kotlin/basic/fileServiceWithPaths/Main.kt")
        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.FILE_SERVICE, path = "/static/")
            .also { route ->
                assertEquals(FileServiceRootKind.FILE_SYSTEM, route.fileServiceRoot?.kind)
                assertEquals("src/main/resources/static", route.fileServiceRoot?.path)
            }
    }

    fun testCollectKotlinFileServiceWithServiceOfRoot() {
        configureFixture("extendedRegistration/kotlin/basic/fileServiceWithServiceOf/Main.kt")
        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.FILE_SERVICE, path = "/static/")
            .also { route ->
                assertEquals(FileServiceRootKind.FILE_SYSTEM, route.fileServiceRoot?.kind)
                assertEquals("src/main/resources/public", route.fileServiceRoot?.path)
            }
    }

    fun testCollectKotlinFluentRouteRegistration() {
        configureFixture("extendedRegistration/kotlin/basic/fluentRoute/Main.kt")
        collectRoutes().also { it.singleRoute() }.assertRoute(
            RouteMatch.ROUTE_FLUENT,
            path = "/api/items",
            httpMethod = "POST",
        )
    }

    fun testCollectKotlinDecoratorUnderRegistration() {
        configureFixture("extendedRegistration/kotlin/basic/decoratorUnder/Main.kt")
        collectRoutes().also { it.singleRoute() }.assertRoute(RouteMatch.DECORATOR_UNDER, path = "/public")
    }

    fun testCollectKotlinWithRouteRegistration() {
        configureFixture("extendedRegistration/kotlin/basic/withRoute/Main.kt")
        collectRoutes().also { it.singleRoute() }.assertRoute(
            RouteMatch.ROUTE_FLUENT,
            path = "/wrapped",
            httpMethod = "POST",
        )
    }

    fun testCollectKotlinWithRouteDoesNotBurnDedupKeyOnInvalidLambda() {
        configureFixture("extendedRegistration/kotlin/basic/withRouteDedup/Main.kt")
        collectRoutes().also { it.singleRoute() }.assertRoute(
            RouteMatch.ROUTE_FLUENT,
            path = "/wrapped",
            httpMethod = "POST",
        )
    }

    fun testCollectKotlinHealthCheckRegistration() {
        configureFixture("extendedRegistration/kotlin/basic/healthCheck/Main.kt")
        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.HEALTH_CHECK, path = "/internal/healthcheck")
            .also { route ->
                assertEquals(RouteProtocol.HEALTH_CHECK.presentableName(), route.protocol)
                assertEquals("GET", route.httpMethod)
            }
    }

    fun testCollectKotlinFluentRoutePathPrefix() {
        configureFixture("extendedRegistration/kotlin/basic/fluentRoutePathPrefix/Main.kt")
        collectRoutes().also { it.singleRoute() }.assertRoute(
            RouteMatch.ROUTE_FLUENT,
            path = "/api/items",
            httpMethod = "GET",
            pathType = PathType.EXACT,
        )
    }

    fun testCollectKotlinFileServiceFromConstValPath() {
        configureFixture("extendedRegistration/kotlin/basic/fileServiceWithConstVal/Main.kt")
        collectRoutes().also { it.singleRoute() }.assertRoute(RouteMatch.FILE_SERVICE, path = "/files/")
    }

    fun testSkipsKotlinFileServiceWithNonConstantPath() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import java.io.File

            fun main() {
                val path = dynamicPath()
                Server.builder()
                    .fileService(path, File("/tmp"))
                    .build()
            }

            private fun dynamicPath(): String = "/dynamic"
            """.trimIndent(),
        )

        val routes = collectRoutes()

        assertTrue(routes.none { it.routeMatch == RouteMatch.FILE_SERVICE })
    }

    fun testSkipsKotlinHealthCheckServiceWithNonConstantPath() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                val path = dynamicPath()
                Server.builder()
                    .healthCheckService(path)
                    .build()
            }

            private fun dynamicPath(): String = "/dynamic"
            """.trimIndent(),
        )

        val routes = collectRoutes()

        assertTrue(routes.none { it.routeMatch == RouteMatch.HEALTH_CHECK })
    }

    fun testSkipsKotlinFluentRouteWithNonConstantPath() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                val path = dynamicPath()
                Server.builder()
                    .route()
                    .post(path)
                    .build(Any())
                    .build()
            }

            private fun dynamicPath(): String = "/dynamic"
            """.trimIndent(),
        )

        val routes = collectRoutes()

        assertTrue(routes.none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testSkipsKotlinFluentRouteWithNonConstantPathPrefix() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                val path = dynamicPath()
                Server.builder()
                    .route()
                    .pathPrefix(path)
                    .get("/items")
                    .build(Any())
                    .build()
            }

            private fun dynamicPath(): String = "/dynamic"
            """.trimIndent(),
        )

        val routes = collectRoutes()

        assertTrue(routes.none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testCollectKotlinFluentRouteWithTwoArgPath() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .route()
                    .path("/api", "/v2")
                    .build(Any())
                    .build()
            }
            """.trimIndent(),
        )

        collectRoutes().also { it.singleRoute() }.assertRoute(
            RouteMatch.ROUTE_FLUENT,
            path = "/api/v2",
        )
    }

    fun testSkipsKotlinFluentRouteWithUnresolvedTwoArgPath() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                val pattern = dynamicPath()
                Server.builder()
                    .route()
                    .path("/api", pattern)
                    .build(Any())
                    .build()
            }

            private fun dynamicPath(): String = "/dynamic"
            """.trimIndent(),
        )

        val routes = collectRoutes()

        assertTrue(routes.none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testCollectsKotlinServiceRegistrationWithFluentRouteArgument() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                val sb = Server.builder()
                sb.service(sb.route().path("/fluent").build(), Any())
            }
            """.trimIndent(),
        )

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/fluent")
    }

    fun testSkipsKotlinServiceRegistrationWithUnresolvedFluentRouteArgument() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                val path = dynamicPath()
                val sb = Server.builder()
                sb.service(sb.route().path(path).build(), Any())
            }

            private fun dynamicPath(): String = "/dynamic"
            """.trimIndent(),
        )

        val routes = collectRoutes()

        assertTrue(routes.none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }
}
