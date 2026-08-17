package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArmeriaAnnotationProcessorInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerContentAnnotationStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaAnnotationProcessorInspection())
    }

    @Test
    fun doesNotHighlightDescriptionWithoutProcessor() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Description;
            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                @Get("/hello")
                @Description("Greets the caller.")
                public String hello() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )
        assertJavadocHighlights(0)
    }

    @Test
    fun doesNotHighlightSummaryOnlyJavadocWithoutProcessor() {
        configureJavadocService("/** Greets the caller. */")
        assertJavadocHighlights(0)
    }

    @Test
    fun highlightsJavadocTagsOnAnnotatedRouteWithoutProcessor() {
        configureJavadocService("/** @param unused unused */")
        assertJavadocHighlights(1)
    }

    @Test
    fun allowsJavadocTagsWhenGradleMentionsProcessor() {
        myFixture.addFileToProject(
            "build.gradle.kts",
            """
            dependencies {
                annotationProcessor("com.linecorp.armeria:armeria-annotation-processor:1.32.0")
            }
            """.trimIndent(),
        )
        configureJavadocService("/** @return greeting */")
        assertJavadocHighlights(0)
    }

    @Test
    fun allowsJavadocTagsWhenProcessorClassResolves() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation.processor;

            public class DocumentationProcessor {}
            """.trimIndent(),
        )
        configureJavadocService("/** @throws RuntimeException if the call fails */")
        assertJavadocHighlights(0)
    }

    private fun configureJavadocService(javadoc: String) {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                $javadoc
                @Get("/hello")
                public String hello() {
                    return "ok";
                }
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
