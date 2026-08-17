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
    fun doesNotHighlightDescriptionWithoutProcessor() {
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
        assertJavadocHighlights(0)
    }

    @Test
    fun doesNotHighlightSummaryOnlyKdocWithoutProcessor() {
        configureKdocService("/** Greets the caller. */")
        assertJavadocHighlights(0)
    }

    @Test
    fun highlightsKdocTagsOnAnnotatedRouteWithoutProcessor() {
        configureKdocService("/** @param unused unused */")
        assertJavadocHighlights(1)
    }

    @Test
    fun allowsKdocTagsWhenGradleMentionsProcessor() {
        myFixture.addFileToProject(
            "build.gradle.kts",
            """
            dependencies {
                kapt("com.linecorp.armeria:armeria-annotation-processor:1.32.0")
            }
            """.trimIndent(),
        )
        configureKdocService("/** @return greeting */")
        assertJavadocHighlights(0)
    }

    private fun configureKdocService(kdoc: String) {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get

            class HelloService {
                $kdoc
                @Get("/hello")
                fun hello(): String = "ok"
            }
            """.trimIndent(),
        )
    }

    private fun assertJavadocHighlights(count: Int) {
        val expected = message("inspection.annotation.processor.javadoc.problem")
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(count, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }
}
