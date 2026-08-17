package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArmeriaAnnotationProcessorKotlinInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerContentAnnotationStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaAnnotationProcessorKotlinInspection())
    }

    @Test
    fun highlightsDescriptionWithoutProcessor() {
        configureDescriptionService()
        assertDescriptionHighlights(1)
    }

    @Test
    fun allowsDescriptionWhenGradleMentionsProcessor() {
        myFixture.addFileToProject(
            "build.gradle.kts",
            """
            dependencies {
                kapt("com.linecorp.armeria:armeria-annotation-processor:1.32.0")
            }
            """.trimIndent(),
        )
        configureDescriptionService()
        assertDescriptionHighlights(0)
    }

    @Test
    fun highlightsKdocOnAnnotatedRouteWithoutProcessor() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                /** Greets the caller. */
                @Get("/hello")
                fun hello(): String = "ok"
            }
            """.trimIndent(),
        )
        val expected = message("inspection.annotation.processor.javadoc.problem")
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(1, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }

    private fun configureDescriptionService() {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Description
            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                @Get("/hello")
                @Description("Greets the caller.")
                fun hello(): String = "ok"
            }
            """.trimIndent(),
        )
    }

    private fun assertDescriptionHighlights(count: Int) {
        val expected = message("inspection.annotation.processor.problem")
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(count, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }
}
