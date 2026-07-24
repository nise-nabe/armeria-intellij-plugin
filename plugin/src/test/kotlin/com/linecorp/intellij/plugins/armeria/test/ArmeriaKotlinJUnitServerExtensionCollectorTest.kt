package com.linecorp.intellij.plugins.armeria.test

import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase

class ArmeriaKotlinJUnitServerExtensionCollectorTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    fun testCollectsRegisterExtensionFromClassProperty() {
        myFixture.configureByText(
            "ExampleServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            class ExampleServiceTest {
                @RegisterExtension
                val server: ServerExtension = object : ServerExtension() {}
            }
            """.trimIndent(),
        )

        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

        assertEquals(1, extensions.size)
        assertEquals("server", extensions.single().variableName)
        assertEquals("example.ExampleServiceTest", extensions.single().containingClassName)
    }

    fun testCollectsRegisterExtensionFromInferredTypeProperty() {
        myFixture.configureByText(
            "ExampleServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            class ExampleServiceTest {
                @RegisterExtension
                val server = object : ServerExtension() {}
            }
            """.trimIndent(),
        )

        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

        assertEquals(1, extensions.size)
        assertEquals("server", extensions.single().variableName)
    }

    fun testCollectsRegisterExtensionFromNestedClassProperty() {
        myFixture.configureByText(
            "ExampleServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            class ExampleServiceTest {
                class NestedTest {
                    @RegisterExtension
                    val server: ServerExtension = object : ServerExtension() {}
                }
            }
            """.trimIndent(),
        )

        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

        assertEquals(1, extensions.size)
        assertEquals("example.ExampleServiceTest.NestedTest", extensions.single().containingClassName)
    }

    fun testCollectsRegisterExtensionWithStarImport() {
        myFixture.configureByText(
            "ExampleServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.*
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            class ExampleServiceTest {
                @RegisterExtension
                val server: ServerExtension = object : ServerExtension() {}
            }
            """.trimIndent(),
        )

        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

        assertEquals(1, extensions.size)
        assertEquals("server", extensions.single().variableName)
    }
}
