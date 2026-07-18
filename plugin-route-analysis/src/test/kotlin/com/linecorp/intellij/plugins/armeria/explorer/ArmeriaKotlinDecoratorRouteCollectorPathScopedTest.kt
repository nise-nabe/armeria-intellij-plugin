package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaKotlinDecoratorRouteCollectorPathScopedTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerKotlinRouteCollectorStubs()
    }

    fun testCollectProgrammaticPathScopedDecoratorOnKotlinServiceRegistration() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder()
                    .decorator("/api/**", LoggingService::class.java)
                    .service("/api", HelloService())
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
        assertNotNull(serviceRoute)
        assertEquals(listOf("Logging"), serviceRoute!!.decorators)
    }

    fun testCollectPathScopedDecoratorFiltersByRoute() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder()
                    .decorator("/api/**", LoggingService::class.java)
                    .service("/api", HelloService())
                    .service("/other", HelloService())
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

        val apiRoute = routes.firstOrNull { it.path == "/api" }
        val otherRoute = routes.firstOrNull { it.path == "/other" }
        assertNotNull(apiRoute)
        assertNotNull(otherRoute)
        assertEquals(listOf("Logging"), apiRoute!!.decorators)
        assertEquals(emptyList<String>(), otherRoute!!.decorators)
    }

    fun testCollectPathScopedDecoratorWithConstValPathPattern() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            private const val API_PATH = "/api/**"

            fun main() {
                Server.builder()
                    .decorator(API_PATH, LoggingService::class.java)
                    .service("/api", HelloService())
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
        assertNotNull(serviceRoute)
        assertEquals(listOf("Logging"), serviceRoute!!.decorators)
    }
}
