package com.linecorp.intellij.plugins.armeria.test

import com.linecorp.intellij.plugins.armeria.explorer.support.toArmeriaLightClass
import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ArmeriaKotlinJUnitServerExtensionCollectorTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.registerArmeriaJUnitTestSupportStubs()
    }

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

    fun testSkipsKotlinServerHiddenByInaccessibleJavaMember() {
        myFixture.addFileToProject(
            "KotlinBaseTest.kt",
            """
            package serverbase

            import com.linecorp.armeria.testing.junit5.server.ServerExtension
            import org.junit.jupiter.api.extension.RegisterExtension

            abstract class KotlinBaseTest {
                @field:RegisterExtension
                @JvmField
                val server: ServerExtension = object : ServerExtension() {}
            }
            """.trimIndent(),
        )
        myFixture.addClass(
            """
            package serverbase;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class JavaParentTest extends KotlinBaseTest {
                @RegisterExtension
                ServerExtension server = new ServerExtension() {};
            }
            """.trimIndent(),
        )
        val javaFile =
            myFixture.configureByText(
                "HiddenKotlinServerTest.java",
                """
                package example;

                import serverbase.JavaParentTest;

                public class HiddenKotlinServerTest extends JavaParentTest {
                }
                """.trimIndent(),
            ) as com.intellij.psi.PsiJavaFile

        val extensions = ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, javaFile.classes.single())

        assertTrue(extensions.isEmpty())
    }

    fun testSkipsOuterInstanceExtensionsFromNonInnerNestedClass() {
        val psiFile =
            myFixture.configureByText(
                "OuterTest.kt",
                """
                package example

                import org.junit.jupiter.api.extension.RegisterExtension
                import com.linecorp.armeria.testing.junit5.server.ServerExtension

                open class BaseTest {
                    @RegisterExtension
                    val inheritedOuterServer: ServerExtension = object : ServerExtension() {}
                }

                class OuterTest : BaseTest() {
                    @RegisterExtension
                    val outerServer: ServerExtension = object : ServerExtension() {}

                    class NestedTest {
                        @RegisterExtension
                        val nestedServer: ServerExtension = object : ServerExtension() {}
                    }
                }
                """.trimIndent(),
            ) as KtFile
        val outerClass = psiFile.declarations.filterIsInstance<KtClass>().single { it.name == "OuterTest" }
        val nestedClass =
            outerClass.declarations
                .filterIsInstance<KtClass>()
                .single()
                .toArmeriaLightClass()!!

        val extensions = ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, nestedClass)

        assertEquals(listOf("nestedServer"), extensions.map { it.variableName })
    }

    fun testCollectsOnlyTestSourceFiles() {
        myFixture.withTemporaryMainSourceRoot {
            myFixture.addFileToProject(
                "main/Production.kt",
                """
                package example

                import org.junit.jupiter.api.extension.RegisterExtension
                import com.linecorp.armeria.testing.junit5.server.ServerExtension

                class Production {
                    @RegisterExtension
                    val productionServer: ServerExtension = object : ServerExtension() {}
                }
                """.trimIndent(),
            )
            myFixture.addFileToProject(
                "ExampleServiceTest.kt",
                """
                package example

                import org.junit.jupiter.api.extension.RegisterExtension
                import com.linecorp.armeria.testing.junit5.server.ServerExtension

                class ExampleServiceTest {
                    @RegisterExtension
                    val testServer: ServerExtension = object : ServerExtension() {}
                }
                """.trimIndent(),
            )

            val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

            assertEquals(listOf("testServer"), extensions.map { it.variableName })
        }
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
