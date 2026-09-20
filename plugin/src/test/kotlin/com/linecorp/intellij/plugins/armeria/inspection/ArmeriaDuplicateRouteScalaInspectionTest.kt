package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArmeriaDuplicateRouteScalaInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaDuplicateRouteScalaInspection())
    }

    @Test
    fun highlightsDuplicateScalaRoutes() {
        myFixture.configureByText(
            "BadService.scala",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class BadService {
                @Get("/dup")
                def first(): String = "first"

                @Get("/dup")
                def second(): String = "second"
            }
            """.trimIndent(),
        )
        assertDuplicateRouteHighlights(2)
    }

    @Test
    fun allowsDistinctScalaPaths() {
        myFixture.configureByText(
            "HelloService.scala",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                def hello(): String = "hello"

                @Get("/goodbye")
                def goodbye(): String = "goodbye"
            }
            """.trimIndent(),
        )
        assertDuplicateRouteHighlights(0)
    }

    @Test
    fun allowsDifferentHttpMethodsOnSamePath() {
        myFixture.configureByText(
            "Routes.scala",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.Post

            class Routes {
                @Get("/resource")
                def read(): String = "read"

                @Post("/resource")
                def write(): String = "write"
            }
            """.trimIndent(),
        )
        assertDuplicateRouteHighlights(0)
    }

    @Test
    fun highlightsDuplicateRoutesWithPathPrefix() {
        myFixture.configureByText(
            "PrefixedService.scala",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.PathPrefix

            @PathPrefix("/api")
            class PrefixedService {
                @Get("/items")
                def first(): String = "first"

                @Get("/items")
                def second(): String = "second"
            }
            """.trimIndent(),
        )
        assertDuplicateRouteHighlights(2)
    }

    private fun assertDuplicateRouteHighlights(expectedCount: Int) {
        val expected = message("inspection.duplicate.route.problem")
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(expectedCount, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }
}
