package com.linecorp.intellij.plugins.armeria.test

import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ArmeriaKotlinJUnitServerExtensionCollectorTest : LightJavaCodeInsightFixtureTestCase() {
    fun testCollectsRegisterExtensionFromCompanionObject() {
        myFixture.configureByText(
            "ExampleServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            class ExampleServiceTest {
                companion object {
                    @RegisterExtension
                    @JvmField
                    val server: ServerExtension = object : ServerExtension() {}
                }
            }
            """.trimIndent(),
        )

        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

        assertEquals(1, extensions.size)
        assertEquals("server", extensions.single().variableName)
        assertEquals("example.ExampleServiceTest", extensions.single().containingClassName)
    }
}
