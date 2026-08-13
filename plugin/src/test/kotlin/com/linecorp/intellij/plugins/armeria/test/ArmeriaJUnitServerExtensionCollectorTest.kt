package com.linecorp.intellij.plugins.armeria.test

import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaJUnitServerExtensionCollectorTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.registerArmeriaJUnitTestSupportStubs()
    }

    fun testCollectsRegisterExtensionFromJavaField() {
        myFixture.configureByText(
            "ExampleServiceTest.java",
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class ExampleServiceTest {
                @RegisterExtension
                static ServerExtension server = new ServerExtension() {};
            }
            """.trimIndent(),
        )

        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

        assertEquals(1, extensions.size)
        assertEquals("server", extensions.single().variableName)
        assertEquals("example.ExampleServiceTest", extensions.single().containingClassName)
    }

    fun testCollectsRegisterExtensionWithStarImport() {
        myFixture.configureByText(
            "ExampleServiceTest.java",
            """
            package example;

            import org.junit.jupiter.api.extension.*;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class ExampleServiceTest {
                @RegisterExtension
                static ServerExtension server = new ServerExtension() {};
            }
            """.trimIndent(),
        )

        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

        assertEquals(1, extensions.size)
        assertEquals("server", extensions.single().variableName)
    }

    fun testCollectsRegisterExtensionFromJavaFactoryMethod() {
        myFixture.configureByText(
            "ExampleServiceTest.java",
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class ExampleServiceTest {
                @RegisterExtension
                static ServerExtension server() {
                    return new ServerExtension() {};
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
            "ExampleServiceTest.java",
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class ExampleServiceTest {
                @RegisterExtension
                ServerExtension server() {
                    return new ServerExtension() {};
                }
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

    fun testIgnoresParameterizedJavaFactoryMethod() {
        myFixture.configureByText(
            "ExampleServiceTest.java",
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class ExampleServiceTest {
                @RegisterExtension
                static ServerExtension server(String ignored) {
                    return new ServerExtension() {};
                }
            }
            """.trimIndent(),
        )

        val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

        assertTrue(extensions.isEmpty())
    }
}
