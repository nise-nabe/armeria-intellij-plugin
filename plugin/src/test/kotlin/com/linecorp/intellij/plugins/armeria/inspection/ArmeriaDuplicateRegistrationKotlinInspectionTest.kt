package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaDuplicateRegistrationKotlinInspectionTest : ArmeriaFixtureTestBase() {

    override fun registerArmeriaStubs() {
        registerRouteDuplicateIndexStubs()
    }

    fun testInClassKotlinAnnotatedDuplicatesAreHighlighted() {
        myFixture.configureByText(
            "BadService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class BadService {
                @Get("/dup")
                fun first(): String = "first"

                @Get("/dup")
                fun second(): String = "second"
            }
            """.trimIndent(),
        )

        myFixture.enableInspections(ArmeriaDuplicateRegistrationKotlinInspection())
        val warnings = warningHighlights()

        assertEquals(2, warnings.size)
        assertTrue(
            warnings.all {
                it.description == message("inspection.duplicate.registration.problem", "GET /dup", 2)
            },
        )
        assertEquals(setOf("first", "second"), warnings.map { it.text }.toSet())
    }

    fun testCrossFileKotlinAnnotatedRoutesAreHighlighted() {
        myFixture.addFileToProject(
            "src/Second.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class Second {
                @Get("/shared")
                fun second(): String = "second"
            }
            """.trimIndent(),
        )
        myFixture.configureByText(
            "First.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class First {
                @Get("/shared")
                fun first(): String = "first"
            }
            """.trimIndent(),
        )

        myFixture.enableInspections(ArmeriaDuplicateRegistrationKotlinInspection())
        val warnings = warningHighlights()

        assertEquals(1, warnings.size)
        assertEquals(
            message("inspection.duplicate.registration.problem", "GET /shared", 2),
            warnings.single().description,
        )
        assertEquals("first", warnings.single().text)
    }

    fun testDistinctKotlinAnnotatedRoutesAreNotHighlighted() {
        myFixture.configureByText(
            "OkService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class OkService {
                @Get("/one")
                fun one(): String = "one"

                @Get("/two")
                fun two(): String = "two"
            }
            """.trimIndent(),
        )

        myFixture.enableInspections(ArmeriaDuplicateRegistrationKotlinInspection())
        assertTrue(warningHighlights().isEmpty())
    }

    private fun warningHighlights(): List<HighlightInfo> =
        myFixture.doHighlighting().filter { it.severity == HighlightSeverity.WARNING }
}
