package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.compiler.CompilerConfiguration
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase5
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ArmeriaParametersCompilerInspectionTest : ArmeriaFixtureTestBase5() {
    override fun registerArmeriaStubs() {
        registerArmeriaAnnotationStubs()
        registerContentAnnotationStubs()
    }

    override fun onFixtureSetUp() {
        super.onFixtureSetUp()
        CompilerConfiguration.getInstance(project).removeAdditionalOptions(module)
        myFixture.enableInspections(ArmeriaParametersCompilerInspection())
    }

    @Test
    fun highlightsUnnamedParamWithoutCompilerFlag() {
        configureHandler("""public String handler(@Param String name) { return name; }""")
        assertParametersHighlights(1)
    }

    @Test
    fun allowsExplicitParamName() {
        configureHandler("""public String handler(@Param("name") String name) { return name; }""")
        assertParametersHighlights(0)
    }

    @Test
    fun allowsUnnamedParamWhenCompilerFlagPresent() {
        CompilerConfiguration.getInstance(project).setAdditionalOptions(module, listOf("-parameters"))
        configureHandler("""public String handler(@Param String name) { return name; }""")
        assertParametersHighlights(0)
    }

    @Test
    fun allowsUnnamedParamWhenGradleCompilerArgsPresent() {
        myFixture.addFileToProject(
            "build.gradle.kts",
            """
            tasks.withType<JavaCompile> {
                options.compilerArgs.add("-parameters")
            }
            """.trimIndent(),
        )
        configureHandler("""public String handler(@Param String name) { return name; }""")
        assertParametersHighlights(0)
    }

    private fun configureHandler(signature: String) {
        myFixture.configureByText(
            "HelloService.java",
            """
            package example;

            import com.linecorp.armeria.server.annotation.Get;
            import com.linecorp.armeria.server.annotation.Param;

            public class HelloService {
                @Get("/hello")
                $signature
            }
            """.trimIndent(),
        )
    }

    private fun assertParametersHighlights(count: Int) {
        val expected = message("inspection.parameters.flag.problem")
        val highlights = myFixture.doHighlighting().filter { it.description == expected }
        assertEquals(count, highlights.size, highlights.joinToString { it.description.orEmpty() })
    }
}
