package com.linecorp.intellij.plugins.armeria.inspection

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
                public String <warning descr="This annotated Armeria route duplicates another HTTP method/path combination in the same service class.">first</warning>() {
                    return "first";
                }

                @Get("/dup")
                public String <warning descr="This annotated Armeria route duplicates another HTTP method/path combination in the same service class.">second</warning>() {
                    return "second";
                }
            }
            """.trimIndent(),
        )

        myFixture.testHighlighting(true, false, true)
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

        myFixture.testHighlighting(true, false, true)
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

        myFixture.testHighlighting(true, false, true)
    }
}
