package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaKotlinExtendedRegistrationCollectorIgnoreTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerKotlinExtendedRegistrationCollectorStubs()
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

    fun testIgnoreNonArmeriaServiceInVirtualHostLambda() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .virtualHost("api.example.com") { vh ->
                        OtherBuilder.builder()
                            .service("/nope", Any())
                            .build()
                    }
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.SERVICE })
    }

    fun testIgnoreNonArmeriaFluentBuildInVirtualHostLambda() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .virtualHost("api.example.com") {
                        OtherBuilder.builder()
                            .route()
                            .post("/nope")
                            .build(Any())
                            .build()
                    }
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        assertTrue(routes.none { it.routeMatch == RouteMatch.ROUTE_FLUENT })
    }
}
