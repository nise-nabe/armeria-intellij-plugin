package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase

class ArmeriaDuplicateRouteKotlinInspectionTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        registerArmeriaStubs()
        myFixture.enableInspections(ArmeriaDuplicateRouteKotlinInspection())
    }

    fun testHighlightsDuplicateKotlinRoutes() {
        myFixture.configureByText(
            "BadService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class BadService {
                @Get("/dup")
                fun <warning descr="This annotated Armeria route duplicates another HTTP method/path combination in the same service class.">first</warning>(): String = "first"

                @Get("/dup")
                fun <warning descr="This annotated Armeria route duplicates another HTTP method/path combination in the same service class.">second</warning>(): String = "second"
            }
            """.trimIndent(),
        )

        myFixture.testHighlighting(true, false, true)
    }

    fun testHighlightsInheritedDuplicateKotlinRoutes() {
        myFixture.configureByText(
            "Services.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            open class BaseService {
                @Get("/dup")
                fun <warning descr="This annotated Armeria route duplicates another HTTP method/path combination in the same service class.">base</warning>(): String = "base"
            }

            class ChildService : BaseService() {
                @Get("/dup")
                fun <warning descr="This annotated Armeria route duplicates another HTTP method/path combination in the same service class.">child</warning>(): String = "child"
            }
            """.trimIndent(),
        )

        myFixture.testHighlighting(true, false, true)
    }

    private fun registerArmeriaStubs() {
        myFixture.configureByText(
            "Get.kt",
            """
            package com.linecorp.armeria.server.annotation

            annotation class Get(val value: String = "", val path: String = "")
            """.trimIndent(),
        )
    }
}
