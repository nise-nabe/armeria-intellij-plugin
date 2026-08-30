package com.linecorp.intellij.plugins.armeria.test

import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    fun testCollectsRegisterExtensionFromObjectDeclaration() {
        myFixture.configureByText(
            "ExampleServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            object ExampleServiceTest {
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

    fun testCollectsRegisterExtensionFromFactoryMethod() {
        myFixture.configureByText(
            "ExampleServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            class ExampleServiceTest {
                companion object {
                    @JvmStatic
                    @RegisterExtension
                    fun server(): ServerExtension = object : ServerExtension() {}
                }
            }
            """.trimIndent(),
        )

        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

        assertEquals(1, extensions.size)
        assertEquals("server", extensions.single().variableName)
        assertTrue(extensions.single().isFactoryMethod)
        assertEquals("server()", extensions.single().serverReceiver)
    }

    fun testCollectsInstanceRegisterExtensionFactoryMethod() {
        myFixture.configureByText(
            "ExampleServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            class ExampleServiceTest {
                @RegisterExtension
                fun server(): ServerExtension = object : ServerExtension() {}
            }
            """.trimIndent(),
        )

        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

        assertEquals(1, extensions.size)
        assertEquals("server", extensions.single().variableName)
        assertTrue(extensions.single().isFactoryMethod)
        assertEquals("server()", extensions.single().serverReceiver)
        assertEquals("example.ExampleServiceTest", extensions.single().containingClassName)
    }

    fun testCollectsRegisterExtensionFromObjectFactoryMethod() {
        myFixture.configureByText(
            "ExampleServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            object ExampleServiceTest {
                @RegisterExtension
                fun server(): ServerExtension = object : ServerExtension() {}
            }
            """.trimIndent(),
        )

        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

        assertEquals(1, extensions.size)
        assertEquals("server", extensions.single().variableName)
        assertTrue(extensions.single().isFactoryMethod)
        assertEquals("server()", extensions.single().serverReceiver)
        assertEquals("example.ExampleServiceTest", extensions.single().containingClassName)
    }

    fun testIgnoresCompanionFactoryMethodWithoutJvmStatic() {
        myFixture.configureByText(
            "ExampleServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            class ExampleServiceTest {
                companion object {
                    @RegisterExtension
                    fun server(): ServerExtension = object : ServerExtension() {}
                }
            }
            """.trimIndent(),
        )

        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

        assertTrue(extensions.isEmpty())
    }

    fun testIgnoresLocalKotlinFactoryMethod() {
        myFixture.configureByText(
            "ExampleServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            class ExampleServiceTest {
                fun testSomething() {
                    @RegisterExtension
                    fun server(): ServerExtension = object : ServerExtension() {}
                }
            }
            """.trimIndent(),
        )

        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

        assertTrue(extensions.isEmpty())
    }

    fun testIgnoresParameterizedKotlinFactoryMethod() {
        myFixture.configureByText(
            "ExampleServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            class ExampleServiceTest {
                @RegisterExtension
                fun server(ignored: String): ServerExtension = object : ServerExtension() {}
            }
            """.trimIndent(),
        )

        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

        assertTrue(extensions.isEmpty())
    }

    fun testIgnoresKotlinExtensionFactoryMethod() {
        myFixture.configureByText(
            "ExampleServiceTest.kt",
            """
            package example

            import org.junit.jupiter.api.extension.RegisterExtension
            import com.linecorp.armeria.testing.junit5.server.ServerExtension

            class ExampleServiceTest {
                @RegisterExtension
                fun String.server(): ServerExtension = object : ServerExtension() {}
            }
            """.trimIndent(),
        )

        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

        assertTrue(extensions.isEmpty())
    }
}
