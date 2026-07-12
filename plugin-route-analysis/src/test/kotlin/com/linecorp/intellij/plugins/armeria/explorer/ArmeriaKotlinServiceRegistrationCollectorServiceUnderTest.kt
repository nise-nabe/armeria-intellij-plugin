package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaKotlinServiceRegistrationCollectorServiceUnderTest : ArmeriaFixtureTestBase() {

    override fun registerArmeriaStubs() {
        registerKotlinRouteCollectorStubs()
    }

    fun testCollectServiceUnderRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .serviceUnder("/v1", HelloService())
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

        val serviceRoute = routes.firstOrNull { it.routeMatch == RouteMatch.SERVICE_UNDER }
        assertNotNull(serviceRoute)
        assertEquals("/v1", serviceRoute!!.path)
        assertEquals("example.HelloService", serviceRoute.target)
    }
    fun testCollectServiceUnderRegistrationWithPathPrefixNamedArgument() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .serviceUnder(service = HelloService(), pathPrefix = "/v1")
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

        val serviceRoute = routes.firstOrNull { it.routeMatch == RouteMatch.SERVICE_UNDER }
        assertNotNull(serviceRoute)
        assertEquals("/v1", serviceRoute!!.path)
        assertEquals("example.HelloService", serviceRoute.target)
    }
}
