package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaKotlinDecoratorRouteCollectorScopeBlockTest : ArmeriaFixtureTestBase() {

    override fun registerArmeriaStubs() {
        registerKotlinRouteCollectorStubs()
    }

    fun testCollectProgrammaticDecoratorInApplyBlock() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder().apply {
                    decorator(LoggingService::class.java)
                    service("/api", HelloService())
                }.build()
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
    fun testCollectProgrammaticDecoratorInAlsoBlock() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder().also {
                    it.decorator(LoggingService::class.java)
                    it.service("/api", HelloService())
                }.build()
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
    fun testCollectProgrammaticDecoratorInAlsoBlockChainedAfterAlso() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder().also {
                    it.decorator(LoggingService::class.java)
                }.service("/api", HelloService())
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
    fun testCollectProgrammaticDecoratorInApplyBlockChainedAfterApply() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder().apply {
                    decorator(LoggingService::class.java)
                }.service("/api", HelloService())
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
    fun testCollectProgrammaticDecoratorInChainedApplyBlocks() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder()
                    .apply { decorator(LoggingService::class.java) }
                    .apply { service("/api", HelloService()) }
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
    fun testCollectProgrammaticDecoratorInApplyBlockStopsAfterRegistrationStatement() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.cors.CorsService
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder().apply {
                    decorator(LoggingService::class.java)
                    val x = service("/api", HelloService())
                    decorator(CorsService::class.java)
                }.build()
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
    fun testCollectProgrammaticDecoratorInApplyBlockStopsWithinRegistrationStatement() {
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server
            import com.linecorp.armeria.server.cors.CorsService
            import com.linecorp.armeria.server.logging.LoggingService

            fun main() {
                Server.builder().apply {
                    decorator(LoggingService::class.java)
                    service("/api", HelloService()).also {
                        decorator(CorsService::class.java)
                    }
                }.build()
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
