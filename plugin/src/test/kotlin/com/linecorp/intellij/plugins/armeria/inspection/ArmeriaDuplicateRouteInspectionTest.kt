package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaDuplicateRouteInspectionTest : ArmeriaFixtureTestBase() {

    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
    }

    fun testDuplicateGetRoutesInSameClassAreHighlighted() {
        myFixture.configureByText(
            "BadService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class BadService {
                @Get("/same")
                public String first() {
                    return "first";
                }

                @Get("/same")
                public String second() {
                    return "second";
                }
            }
            """.trimIndent(),
        )

        myFixture.enableInspections(ArmeriaDuplicateRouteInspection())
        val warnings = warningHighlights()

        assertEquals(2, warnings.size)
        assertTrue(warnings.all { it.description == message("inspection.duplicate.route.problem") })
        assertEquals(setOf("first", "second"), warnings.map { it.text }.toSet())
    }

    fun testDistinctPathsAreNotHighlighted() {
        myFixture.configureByText(
            "OkService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class OkService {
                @Get("/one")
                public String one() {
                    return "one";
                }

                @Get("/two")
                public String two() {
                    return "two";
                }
            }
            """.trimIndent(),
        )

        myFixture.enableInspections(ArmeriaDuplicateRouteInspection())
        assertTrue(warningHighlights().isEmpty())
    }

    fun testDifferentHttpMethodsOnSamePathAreNotHighlighted() {
        myFixture.configureByText(
            "OkService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Post;

            public class OkService {
                @Get("/resource")
                public String read() {
                    return "read";
                }

                @Post("/resource")
                public String write() {
                    return "write";
                }
            }
            """.trimIndent(),
        )

        myFixture.enableInspections(ArmeriaDuplicateRouteInspection())
        assertTrue(warningHighlights().isEmpty())
    }

    private fun warningHighlights(): List<HighlightInfo> =
        myFixture.doHighlighting().filter { it.severity == HighlightSeverity.WARNING }
}
