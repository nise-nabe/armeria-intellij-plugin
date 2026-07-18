package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class ArmeriaKotlinMethodRouteTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;
            public @interface Get { String value() default ""; String path() default ""; }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package example.other;
            public @interface Get { String value() default ""; }
            """.trimIndent(),
        )
    }

    fun testDetectsDuplicateKotlinAnnotatedRoutes() {
        val file =
            configureKotlinService(
                """
                class HelloService {
                    @Get("/hello")
                    fun first(): String = "first"

                    @Get("/hello")
                    fun second(): String = "second"
                }
                """.trimIndent(),
            )
        val functions = functionsIn(file)
        val routes = functions.mapNotNull(ArmeriaKotlinMethodRoute::from)
        val duplicateKeys =
            routes
                .flatMap { route -> route.paths.map { route.httpMethod to it } }
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }

        assertEquals(setOf("GET" to "/hello"), duplicateKeys.keys)
    }

    fun testPrefersValueOverPathAnnotationArgument() {
        val file =
            configureKotlinService(
                """
                class HelloService {
                    @Get(value = "/value", path = "/path")
                    fun hello(): String = "hello"
                }
                """.trimIndent(),
            )
        val route = functionsIn(file).mapNotNull(ArmeriaKotlinMethodRoute::from).single()

        assertEquals(listOf("/value"), route.paths)
    }

    fun testIgnoresNonArmeriaGetAnnotation() {
        val file =
            myFixture.configureByText(
                "HelloService.kt",
                """
                package example

                import example.other.Get

                class HelloService {
                    @Get("/other")
                    fun hello(): String = "hello"
                }
                """.trimIndent(),
            ) as KtFile
        val routes = functionsIn(file).mapNotNull(ArmeriaKotlinMethodRoute::from)

        assertTrue(routes.isEmpty())
    }

    fun testAllowsDistinctPaths() {
        val file =
            configureKotlinService(
                """
                class HelloService {
                    @Get("/hello")
                    fun hello(): String = "hello"

                    @Get("/goodbye")
                    fun goodbye(): String = "goodbye"
                }
                """.trimIndent(),
            )
        val routes = functionsIn(file).mapNotNull(ArmeriaKotlinMethodRoute::from)

        assertEquals(2, routes.size)
        assertEquals(setOf("/hello", "/goodbye"), routes.flatMap { it.paths }.toSet())
    }

    fun testDetectsRoutesInsideObjectDeclaration() {
        val file =
            configureKotlinService(
                """
                object HelloService {
                    @Get("/hello")
                    fun hello(): String = "hello"
                }
                """.trimIndent(),
            )
        val route = functionsIn(file).mapNotNull(ArmeriaKotlinMethodRoute::from).single()

        assertEquals(listOf("/hello"), route.paths)
    }

    fun testFormatsTypedHandlerPaths() {
        val file =
            configureKotlinService(
                """
                class HelloService {
                    @Get("prefix:/hello")
                    fun hello(): String = "hello"
                }
                """.trimIndent(),
            )
        val route = functionsIn(file).mapNotNull(ArmeriaKotlinMethodRoute::from).single()

        assertEquals(listOf("prefix:/hello"), route.paths)
    }

    fun testCombinesClassPrefixWithTypedHandlerPath() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;
            public @interface PathPrefix { String value() default ""; }
            """.trimIndent(),
        )
        val file =
            configureKotlinService(
                """
                import com.linecorp.armeria.server.annotation.PathPrefix

                @PathPrefix("/api")
                class HelloService {
                    @Get("prefix:/hello")
                    fun hello(): String = "hello"
                }
                """.trimIndent(),
            )
        val route = functionsIn(file).mapNotNull(ArmeriaKotlinMethodRoute::from).single()

        assertEquals(listOf("prefix:/api/hello"), route.paths)
    }

    fun testCollectsMultiplePathsFromAnnotation() {
        val file =
            configureKotlinService(
                """
                class HelloService {
                    @Get("/one", "/two")
                    fun hello(): String = "hello"
                }
                """.trimIndent(),
            )
        val route = functionsIn(file).mapNotNull(ArmeriaKotlinMethodRoute::from).single()

        assertEquals(listOf("/one", "/two"), route.paths)
    }

    private fun configureKotlinService(body: String) =
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            $body
            """.trimIndent(),
        ) as KtFile

    private fun functionsIn(file: KtFile): List<KtNamedFunction> {
        val container = file.declarations.filterIsInstance<KtClassOrObject>().single()
        return container.declarations.filterIsInstance<KtNamedFunction>()
    }
}
