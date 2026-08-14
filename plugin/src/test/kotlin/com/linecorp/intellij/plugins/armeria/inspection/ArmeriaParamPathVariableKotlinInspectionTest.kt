package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaParamPathVariableKotlinInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerContentAnnotationStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaParamPathVariableKotlinInspection())
    }

    @Test
    fun highlightsMissingPathVariable() {
        myFixture.configureByText(
            "MissingService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class MissingService {
                @Get("/users/{id}")
                fun handler(): String = "ok"
            }
            """.trimIndent(),
        )
        val expected = message("inspection.param.path.variable.missing", "id")
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(1, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }

    @Test
    fun allowsMatchingParam() {
        myFixture.configureByText(
            "UserService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.Param

            class UserService {
                @Get("/users/{id}")
                fun handler(@Param("id") id: String): String = id
            }
            """.trimIndent(),
        )
        assertNoParamMismatchHighlights()
    }

    @Test
    fun allowsParamWithoutValueUsingParameterName() {
        myFixture.configureByText(
            "UserService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.Param

            class UserService {
                @Get("/hello/:name")
                fun handler(@Param name: String): String = name
            }
            """.trimIndent(),
        )
        assertNoParamMismatchHighlights()
    }

    @Test
    fun allowsQueryParamWhenPathVariableIsMissing() {
        myFixture.configureByText(
            "MismatchService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.Param

            class MismatchService {
                @Get("/users/{id}")
                fun handler(@Param("userId") userId: String, @Param("page") page: Int): String = userId
            }
            """.trimIndent(),
        )
        val descriptions = myFixture.doHighlighting().mapNotNull { it.description }.toSet()
        assertTrue(message("inspection.param.path.variable.missing", "id") in descriptions)
        assertTrue(descriptions.none { it.startsWith("@Param") })
    }

    private fun assertNoParamMismatchHighlights() {
        val highlights =
            myFixture.doHighlighting().filter {
                it.description?.startsWith("Path variable") == true ||
                    it.description?.startsWith("@Param") == true
            }
        assertTrue(highlights.isEmpty(), highlights.joinToString { it.description.orEmpty() })
    }
}
