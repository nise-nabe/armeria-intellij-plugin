package com.linecorp.intellij.plugins.armeria.inspection

import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArmeriaParametersCompilerKotlinInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerContentAnnotationStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        myFixture.enableInspections(ArmeriaParametersCompilerKotlinInspection())
    }

    @Test
    fun highlightsUnnamedParamWithoutCompilerFlag() {
        configureHandler("@Param name: String")
        assertParametersHighlights(1)
    }

    @Test
    fun allowsExplicitParamName() {
        configureHandler("""@Param("name") name: String""")
        assertParametersHighlights(0)
    }

    @Test
    fun allowsUnnamedParamWhenJavaParametersPresent() {
        myFixture.addFileToProject(
            "build.gradle.kts",
            """
            kotlin {
                compilerOptions {
                    javaParameters.set(true)
                }
            }
            """.trimIndent(),
        )
        configureHandler("@Param name: String")
        assertParametersHighlights(0)
    }

    private fun configureHandler(parameter: String) {
        myFixture.configureByText(
            "HelloService.kt",
            """
            package example

            import com.linecorp.armeria.server.annotation.Get
            import com.linecorp.armeria.server.annotation.Param

            class HelloService {
                @Get("/hello")
                fun handler($parameter): String = "ok"
            }
            """.trimIndent(),
        )
    }

    private fun assertParametersHighlights(count: Int) {
        val expected = message("inspection.parameters.flag.kotlin.problem")
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(count, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }
}
