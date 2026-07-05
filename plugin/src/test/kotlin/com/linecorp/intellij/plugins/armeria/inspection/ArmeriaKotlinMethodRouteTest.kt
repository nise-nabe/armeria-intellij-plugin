package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction

class ArmeriaKotlinMethodRouteTest : LightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation;
            public @interface Get { String value() default ""; String path() default ""; }
            """.trimIndent(),
        )
    }

    fun testDetectsDuplicateKotlinAnnotatedRoutes() {
        val file = configureKotlinService(
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
        val duplicateKeys = routes
            .flatMap { route -> route.paths.map { route.httpMethod to it } }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }

        assertEquals(setOf("GET" to "/hello"), duplicateKeys.keys)
    }

    fun testAllowsDistinctPaths() {
        val file = configureKotlinService(
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
        val klass = file.declarations.filterIsInstance<KtClass>().single()
        return klass.declarations.filterIsInstance<KtNamedFunction>()
    }
}
