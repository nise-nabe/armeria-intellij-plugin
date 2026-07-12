package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaKotlinExtendedRegistrationCollectorRouteDecoratorTest : ArmeriaFixtureTestBase() {

    override fun registerArmeriaStubs() {
        registerKotlinExtendedRegistrationCollectorStubs()
    }

    fun testCollectKotlinRouteDecoratorDefaultPathTypeIsGlob() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder()
                    .routeDecorator()
                    .build(LoggingService.newDecorator())
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val decoratorRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ROUTE_DECORATOR }
        assertNotNull(decoratorRoute)
        assertEquals(PathType.GLOB, decoratorRoute!!.pathType)
        assertEquals("/**", decoratorRoute.path)
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
        val decoratorRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ROUTE_DECORATOR }
        assertNotNull(decoratorRoute)
        assertEquals(PathType.EXACT, decoratorRoute!!.pathType)
        assertEquals("/decorated/**", decoratorRoute.path)
    }


    fun testCollectKotlinRouteDecoratorMethodsStripHttpMethodPrefix() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.common.HttpMethod
            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder()
                    .routeDecorator()
                    .methods(HttpMethod.POST, HttpMethod.PUT)
                    .build(LoggingService.newDecorator())
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val decoratorRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ROUTE_DECORATOR }
        assertNotNull(decoratorRoute)
        assertEquals("POST, PUT", decoratorRoute!!.httpMethod)
    }


    fun testCollectKotlinRouteDecoratorIgnoresPathCallsInsideDecoratorLambda() {
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
                    .build(decoratorWithNestedPath(LoggingService.newDecorator()))
                    .build()
            }

            private fun decoratorWithNestedPath(decorator: Any): Any {
                LoggingService.builder().path("/wrong/**").build()
                return decorator
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)
        val decoratorRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ROUTE_DECORATOR }
        assertNotNull(decoratorRoute)
        assertEquals("/decorated/**", decoratorRoute!!.path)
    }


}
