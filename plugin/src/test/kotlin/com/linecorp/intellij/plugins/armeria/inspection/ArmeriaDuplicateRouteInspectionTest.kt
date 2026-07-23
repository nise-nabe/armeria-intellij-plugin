package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiJavaFile
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase

class ArmeriaDuplicateRouteInspectionTest : ArmeriaFixtureTestBase() {
    override fun registerArmeriaStubs() {
        registerRouteDuplicateIndexStubs()
    }

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(ArmeriaDuplicateRouteInspection())
    }

    fun testHighlightsDuplicateJavaRoutes() {
        myFixture.configureByText(
            "BadService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class BadService {
                @Get("/dup")
                public String first() {
                    return "first";
                }

                @Get("/dup")
                public String second() {
                    return "second";
                }
            }
            """.trimIndent(),
        )

        assertDuplicateRouteHighlightsOnMethods("BadService", "first", "BadService", "second")
    }

    fun testAllowsDistinctPaths() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                public String hello() {
                    return "hello";
                }

                @Get("/goodbye")
                public String goodbye() {
                    return "goodbye";
                }
            }
            """.trimIndent(),
        )

        assertNoDuplicateRouteHighlights()
    }

    fun testAllowsDifferentHttpMethodsOnSamePath() {
        myFixture.configureByText(
            "Routes.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Post;

            public class Routes {
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

        assertNoDuplicateRouteHighlights()
    }

    private fun assertNoDuplicateRouteHighlights() {
        val routeDuplicateHighlights =
            myFixture.doHighlighting().filter {
                it.description == message("inspection.duplicate.route.problem")
            }

        assertTrue(routeDuplicateHighlights.isEmpty())
    }

    private fun assertDuplicateRouteHighlightsOnMethods(vararg classAndMethodNames: String) {
        require(classAndMethodNames.size % 2 == 0) { "Expected class/method pairs" }

        val expectedDescription = message("inspection.duplicate.route.problem")
        val duplicateHighlights =
            myFixture.doHighlighting().filter {
                it.description == expectedDescription
            }

        assertEquals(classAndMethodNames.size / 2, duplicateHighlights.size)

        val file = myFixture.file as PsiJavaFile
        val highlightedOffsets = duplicateHighlights.map { it.startOffset }.toSet()
        classAndMethodNames.toList().chunked(2).forEach { (className, methodName) ->
            val clazz =
                file.classes.singleOrNull { it.name == className }
                    ?: error("Class $className not found in ${file.name}")
            val method = clazz.findMethodsByName(methodName, false).single()
            val methodOffset = method.nameIdentifier!!.textRange.startOffset
            assertTrue(
                "Expected duplicate route highlight on $className.$methodName",
                methodOffset in highlightedOffsets,
            )
        }
    }
}
