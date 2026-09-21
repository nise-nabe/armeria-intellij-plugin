package com.linecorp.intellij.plugins.armeria.explorer

import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaRouteDetailFormatter
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull as kotlinAssertNotNull

class ArmeriaKotlinAnnotatedRouteCollectorTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerKotlinRouteCollectorStubs()
    }

    fun testCollectAnnotatedRouteFromKotlinLightMethod() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertEquals(1, routes.size)
        val route = routes.single()
        assertEquals("/hello", route.path)
        assertEquals("GET", route.httpMethod)
        assertEquals(RouteMatch.ANNOTATED_HTTP, route.routeMatch)
        assertTrue(route.target.contains("HelloService#hello"))
    }

    fun testCollectAnnotatedServiceRegistration() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .annotatedService(HelloService())
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val registrationRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ANNOTATED_SERVICE }
        kotlinAssertNotNull(registrationRoute)
        assertEquals("/", registrationRoute.path)
        assertFalse(registrationRoute.annotatedServiceHasPathPrefix)
        assertEquals(
            "Server.builder().annotatedService(…)",
            ArmeriaRouteDetailFormatter.registrationSummary(registrationRoute),
        )

        val annotatedMethodRoute = routes.firstOrNull { it.path == "/hello" }
        kotlinAssertNotNull(annotatedMethodRoute)
        assertEquals(RouteMatch.ANNOTATED_HTTP, annotatedMethodRoute.routeMatch)
    }

    fun testCollectPathPrefix() {
        myFixture.configureByText(
            "PrefixedService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.PathPrefix

            @PathPrefix("/v1")
            class PrefixedService {
                @Get("/items")
                fun items(): String = "items"
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertEquals(1, routes.size)
        assertEquals("/v1/items", routes.single().path)
    }

    fun testCollectAnnotatedServiceWithPathPrefixNamedArgument() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .annotatedService(service = HelloService(), pathPrefix = "/v1")
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val registrationRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ANNOTATED_SERVICE }
        kotlinAssertNotNull(registrationRoute)
        assertEquals("/v1", registrationRoute.path)
        assertTrue(registrationRoute.annotatedServiceHasPathPrefix)
    }

    fun testCollectAnnotatedRouteFromKotlin_multipleHttpMethodAnnotations() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;

            public @interface Post {
                String value() default "";
                String path() default "";
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.Post

            class HelloService {
                @Get("/hello")
                @Post("/hello")
                fun hello(): String = "hello"
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertEquals(
            setOf("GET" to "/hello", "POST" to "/hello"),
            routes.map { it.httpMethod to it.path }.toSet(),
        )
    }

    fun testCollectAnnotatedServiceWithPathPrefix() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                fun hello(): String = "hello"
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "Main.kt",
            """
            package example

            import com.linecorp.armeria.server.Server

            fun main() {
                Server.builder()
                    .annotatedService("/v1", HelloService())
                    .build()
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        val registrationRoute = routes.firstOrNull { it.routeMatch == RouteMatch.ANNOTATED_SERVICE }
        kotlinAssertNotNull(registrationRoute)
        assertEquals("/v1", registrationRoute.path)
        assertTrue(registrationRoute.annotatedServiceHasPathPrefix)
    }

    fun testCollectAnnotatedRouteFromKotlinLightMethod_constantPropertyPath() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            const val PATH = "/hello"

            class HelloService {
                @Get(PATH)
                fun hello(): String = "hello"
            }
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertEquals(
            setOf("GET" to "/hello"),
            routes.map { it.httpMethod to it.path }.toSet(),
        )
    }

    fun testCollectAnnotatedRouteFromKotlinLightMethod_skipsUnresolvedPathArgument() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            val dynamicPath = computePath()

            class HelloService {
                @Get(dynamicPath)
                fun hello(): String = "hello"
            }

            fun computePath(): String = "/hello"
            """.trimIndent(),
        )

        val routes = ArmeriaRouteCollector.collect(project)

        assertTrue(
            routes.none { it.routeMatch == RouteMatch.ANNOTATED_HTTP },
            "routes=${routes.map { it.httpMethod to it.path }}",
        )
    }
}
