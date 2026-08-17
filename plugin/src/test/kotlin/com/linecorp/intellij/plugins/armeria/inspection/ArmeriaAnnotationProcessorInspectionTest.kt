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
                annotationProcessor("com.linecorp.armeria:armeria-annotation-processor:1.32.0")
            }
            """.trimIndent(),
        )
        configureDescriptionService()
        assertDescriptionHighlights(0)
    }

    @Test
    fun allowsDescriptionWhenProcessorClassResolves() {
        myFixture.addClass(
            """
            package com.linecorp.armeria.server.annotation.processor;

            public class DocumentationProcessor {}
            """.trimIndent(),
        )
        configureDescriptionService()
        assertDescriptionHighlights(0)
    }

    @Test
    fun highlightsJavadocOnAnnotatedRouteWithoutProcessor() {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;

            public class HelloService {
                /** Greets the caller. */
                @Get("/hello")
                public String hello() {
                    return "ok";
                }
            }
            """.trimIndent(),
        )
        val expected = message("inspection.annotation.processor.javadoc.problem")
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(1, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }

    private fun configureDescriptionService() {
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
    }

    private fun assertDescriptionHighlights(count: Int) {
        val expected = message("inspection.annotation.processor.problem")
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(count, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }
}
