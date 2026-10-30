package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.model.PathType
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import com.linecorp.intellij.plugins.armeria.test.assertRoute
import com.linecorp.intellij.plugins.armeria.test.singleRoute
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaKotlinExtendedRegistrationCollectorRouteValueTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerKotlinExtendedRegistrationCollectorStubs()
    }

    fun testCollectsServiceRegistrationWithRouteBuilder() {
        configureService(
            """
            Server.builder()
                .service(Route.builder().path("/x").methods(HttpMethod.POST).build(), HelloService())
                .build()
            """,
        )

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/x", httpMethod = "POST")
            .also { route -> assertTrue(route.target.endsWith("HelloService")) }
    }

    fun testCollectsServiceRegistrationWithRouteOf() {
        configureService(
            """
            Server.builder()
                .service(Route.of("/x"), HelloService())
                .build()
            """,
        )

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/x", httpMethod = "", pathType = PathType.EXACT)
            .also { route -> assertTrue(route.target.endsWith("HelloService")) }
    }

    fun testCollectsServiceRegistrationWithRouteOfMethodAndPath() {
        configureService(
            """
            Server.builder()
                .service(Route.of(HttpMethod.POST, "/x"), HelloService())
                .build()
            """,
        )

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/x", httpMethod = "POST")
    }

    fun testCollectsServiceRegistrationWithRouteVariableInitializer() {
        configureService(
            """
            val route = Route.builder().path("/x").build()
            Server.builder()
                .service(route, HelloService())
                .build()
            """,
        )

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/x")
            .also { route -> assertTrue(route.target.endsWith("HelloService")) }
    }

    fun testCollectsServiceRegistrationWithRouteReturningMethod() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.common.HttpMethod
            import com.linecorp.armeria.server.Route
            import com.linecorp.armeria.server.Server

            class HelloService

            fun main() {
                Server.builder()
                    .service(getRoute(), HelloService())
                    .build()
            }

            fun getRoute(): Route = Route.builder().path("/x").methods(HttpMethod.POST).build()
            """.trimIndent(),
        )

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/x", httpMethod = "POST")
            .also { route -> assertTrue(route.target.endsWith("HelloService")) }
    }

    fun testSkipsServiceRegistrationWhenRouteValueCannotBeResolved() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Route
            import com.linecorp.armeria.server.Server

            class HelloService

            fun main() {
                val route = Route.builder().path(dynamicPath()).build()
                Server.builder()
                    .service(route, HelloService())
                    .service(dynamicRoute(), HelloService())
                    .build()
            }

            fun dynamicRoute(): Route = Route.of(dynamicPath())

            fun dynamicPath(): String = "/dynamic"
            """.trimIndent(),
        )

        assertTrue(collectRoutes().none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testSkipsServiceRegistrationWhenRouteMethodReturnsDifferentPaths() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Route
            import com.linecorp.armeria.server.Server

            class HelloService

            fun main() {
                Server.builder()
                    .service(pick(true), HelloService())
                    .build()
            }

            fun pick(flag: Boolean): Route {
                if (flag) {
                    return Route.of("/a")
                }
                return Route.of("/b")
            }
            """.trimIndent(),
        )

        assertTrue(collectRoutes().none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testCollectsStandaloneRouteBuilderBuildWithHandler() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Route

            class HelloService

            fun main() {
                Route.builder().path("/x").build(HelloService())
            }
            """.trimIndent(),
        )

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/x")
            .also { route -> assertTrue(route.target.contains("HelloService")) }
    }

    fun testSkipsUnmountedRouteBuilderBuild() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Route

            fun main() {
                Route.builder().path("/services/greet").build()
                Route.of("/services/hello")
            }
            """.trimIndent(),
        )

        assertTrue(collectRoutes().none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testSkipsServerBuilderBuildWithHandler() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            class HelloService

            fun main() {
                Server.builder().post("/not-a-route").build(HelloService())
            }
            """.trimIndent(),
        )

        assertTrue(collectRoutes().none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testSkipsNonArmeriaRouteOf() {
        myFixture.addClass(
            """
            package example;

            public final class Route {
                public static Object of(String path) {
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

            class HelloService

            fun main() {
                Server.builder()
                    .service(Route.of("/x"), HelloService())
                    .build()
            }
            """.trimIndent(),
        )

        assertTrue(collectRoutes().none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }

    fun testCollectsDecoratorOnEnclosingServiceChain() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Route
            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            class HelloService

            fun main() {
                Server.builder()
                    .decorator(LoggingService::class.java)
                    .service(Route.builder().path("/x").build(), HelloService())
                    .build()
            }
            """.trimIndent(),
        )

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/x")
            .also { route -> assertEquals(listOf("Logging"), route.decorators) }
    }

    fun testWithRouteServiceArgumentKeepsServiceTarget() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.RouteBuilder
            import com.linecorp.armeria.server.Server

            class HelloService

            fun main() {
                Server.builder()
                    .withRoute { route: RouteBuilder ->
                        Server.builder().service(route.route().post("/wrapped").build(), HelloService())
                        route
                    }
                    .build()
            }
            """.trimIndent(),
        )

        collectRoutes()
            .also { it.singleRoute() }
            .assertRoute(RouteMatch.ROUTE_FLUENT, path = "/wrapped", httpMethod = "POST")
            .also { route -> assertTrue(route.target.endsWith("HelloService")) }
    }

    private fun configureService(body: String) {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.common.HttpMethod
            import com.linecorp.armeria.server.Route
            import com.linecorp.armeria.server.Server

            class HelloService

            fun main() {
                ${body.trimIndent()}
            }
            """.trimIndent(),
        )
    }
}
