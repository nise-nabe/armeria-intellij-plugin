package com.linecorp.intellij.plugins.armeria.test

import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaReferenceFileIndex
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    fun testCollectsOnlyTestSourceFiles() {
        myFixture.withTemporaryMainSourceRoot {
            val productionFile =
                myFixture.addFileToProject(
                    "main/Production.java",
                    """
                    package example;

                    import org.junit.jupiter.api.extension.RegisterExtension;
                    import com.linecorp.armeria.testing.junit5.server.ServerExtension;

                    public class Production {
                        @RegisterExtension
                        static ServerExtension productionServer = new ServerExtension() {};
                    }
                    """.trimIndent(),
                )
            val testFile =
                myFixture.addFileToProject(
                    "ExampleServiceTest.java",
                    """
                    package example;

                    import org.junit.jupiter.api.extension.RegisterExtension;
                    import com.linecorp.armeria.testing.junit5.server.ServerExtension;

                    public class ExampleServiceTest {
                        @RegisterExtension
                        static ServerExtension testServer = new ServerExtension() {};
                    }
                    """.trimIndent(),
                )
            assertTrue(ArmeriaJUnitServerExtensionSupport.isInTestSourceContent(testFile))
            assertFalse(ArmeriaJUnitServerExtensionSupport.isInTestSourceContent(productionFile))
            val scope = GlobalSearchScope.projectScope(project)
            val candidates = ArmeriaReferenceFileIndex.findJUnitServerExtensionFiles(project, scope)
            assertTrue(productionFile.virtualFile in candidates)
            assertTrue(testFile.virtualFile in candidates)

            val extensions = ArmeriaJUnitServerExtensionCollector.collect(project)

            assertEquals(listOf("testServer"), extensions.map { it.variableName })
        }
    }

    fun testKeepsOuterServerWithSameNameAsNestedServer() {
        val javaFile =
            myFixture.configureByText(
                "OuterTest.java",
                """
                package example;

                import org.junit.jupiter.api.extension.RegisterExtension;
                import com.linecorp.armeria.testing.junit5.server.ServerExtension;

                public class OuterTest {
                    @RegisterExtension
                    static ServerExtension server = new ServerExtension() {};

                    public static class NestedTest {
                        @RegisterExtension
                        static ServerExtension server = new ServerExtension() {};
                    }
                }
                """.trimIndent(),
            ) as PsiJavaFile
        val nestedClass =
            javaFile
                .classes
                .single()
                .innerClasses
                .single()

        val extensions = ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, nestedClass)

        assertEquals(2, extensions.size)
        assertEquals(listOf("server", "server"), extensions.map { it.variableName })
    }

    fun testSkipsOuterInstanceServerFromStaticNestedClass() {
        val javaFile =
            myFixture.configureByText(
                "OuterInstanceTest.java",
                """
                package example;

                import org.junit.jupiter.api.extension.RegisterExtension;
                import com.linecorp.armeria.testing.junit5.server.ServerExtension;

                public class OuterInstanceTest {
                    @RegisterExtension
                    ServerExtension outerServer = new ServerExtension() {};

                    public static class NestedTest {
                        @RegisterExtension
                        static ServerExtension server = new ServerExtension() {};
                    }
                }
                """.trimIndent(),
            ) as PsiJavaFile
        val nestedClass =
            javaFile
                .classes
                .single()
                .innerClasses
                .single()

        val extensions = ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, nestedClass)

        assertEquals(listOf("server"), extensions.map { it.variableName })
    }

    fun testKeepsSameNameSuperclassServerAmbiguous() {
        myFixture.addClass(
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class BaseTest {
                @RegisterExtension
                static ServerExtension server = new ServerExtension() {};
            }
            """.trimIndent(),
        )
        val javaFile =
            myFixture.configureByText(
                "HidingTest.java",
                """
                package example;

                import org.junit.jupiter.api.extension.RegisterExtension;
                import com.linecorp.armeria.testing.junit5.server.ServerExtension;

                public class HidingTest extends BaseTest {
                    @RegisterExtension
                    static ServerExtension server = new ServerExtension() {};
                }
                """.trimIndent(),
            ) as PsiJavaFile

        val extensions = ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, javaFile.classes.single())

        assertEquals(setOf("example.HidingTest", "example.BaseTest"), extensions.map { it.containingClassName }.toSet())
    }

    fun testKeepsInheritedFieldWhenSubclassFactoryMethodHasSameName() {
        myFixture.addClass(
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class BaseFieldTest {
                @RegisterExtension
                static ServerExtension server = new ServerExtension() {};
            }
            """.trimIndent(),
        )
        val javaFile =
            myFixture.configureByText(
                "FactoryMethodTest.java",
                """
                package example;

                import org.junit.jupiter.api.extension.RegisterExtension;
                import com.linecorp.armeria.testing.junit5.server.ServerExtension;

                public class FactoryMethodTest extends BaseFieldTest {
                    @RegisterExtension
                    static ServerExtension server() {
                        return new ServerExtension() {};
                    }
                }
                """.trimIndent(),
            ) as PsiJavaFile

        val extensions = ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, javaFile.classes.single())

        assertEquals(2, extensions.size)
        assertEquals(setOf(false, true), extensions.map { it.isFactoryMethod }.toSet())
    }

    fun testCombinesOwnAndInterfaceServers() {
        myFixture.addClass(
            """
            package example;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public interface BaseTestInterface {
                @RegisterExtension
                ServerExtension interfaceServer = new ServerExtension() {};
            }
            """.trimIndent(),
        )
        val javaFile =
            myFixture.configureByText(
                "InterfaceServerTest.java",
                """
                package example;

                import org.junit.jupiter.api.extension.RegisterExtension;
                import com.linecorp.armeria.testing.junit5.server.ServerExtension;

                public class InterfaceServerTest implements BaseTestInterface {
                    @RegisterExtension
                    static ServerExtension server = new ServerExtension() {};
                }
                """.trimIndent(),
            ) as PsiJavaFile

        val extensions = ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, javaFile.classes.single())

        assertEquals(setOf("server", "interfaceServer"), extensions.map { it.variableName }.toSet())
    }

    fun testSkipsPackagePrivateSuperclassServerFromDifferentPackage() {
        myFixture.addClass(
            """
            package serverbase;

            import org.junit.jupiter.api.extension.RegisterExtension;
            import com.linecorp.armeria.testing.junit5.server.ServerExtension;

            public class BaseServerTest {
                @RegisterExtension
                static ServerExtension inheritedServer = new ServerExtension() {};
            }
            """.trimIndent(),
        )
        val javaFile =
            myFixture.configureByText(
                "OuterVisibilityTest.java",
                """
                package example;

                import org.junit.jupiter.api.extension.RegisterExtension;
                import com.linecorp.armeria.testing.junit5.server.ServerExtension;
                import serverbase.BaseServerTest;

                public class OuterVisibilityTest extends BaseServerTest {
                    public static class NestedTest {
                        @RegisterExtension
                        static ServerExtension server = new ServerExtension() {};
                    }
                }
                """.trimIndent(),
            ) as PsiJavaFile
        val nestedClass =
            javaFile
                .classes
                .single()
                .innerClasses
                .single()

        val extensions = ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, nestedClass)

        assertEquals(listOf("server"), extensions.map { it.variableName })
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
