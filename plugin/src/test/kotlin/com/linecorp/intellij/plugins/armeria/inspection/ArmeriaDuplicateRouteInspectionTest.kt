package com.linecorp.intellij.plugins.armeria.inspection

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

        val expectedDescription = message("inspection.duplicate.route.problem")
        val duplicateHighlights =
            myFixture.doHighlighting().filter {
                it.description == expectedDescription
            }

        assertEquals(2, duplicateHighlights.size)
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

        val routeDuplicateHighlights =
            myFixture.doHighlighting().filter {
                it.description == message("inspection.duplicate.route.problem")
            }

        assertTrue(routeDuplicateHighlights.isEmpty())
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

        val routeDuplicateHighlights =
            myFixture.doHighlighting().filter {
                it.description == message("inspection.duplicate.route.problem")
            }

        assertTrue(routeDuplicateHighlights.isEmpty())
    }
}
