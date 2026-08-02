package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertNotNull as kotlinAssertNotNull

class ArmeriaTestMethodInserterTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.registerArmeriaJUnitTestSupportStubs()
    }

    fun testResolvesKtClassFromLightClass() {
        val psiFile =
            myFixture.configureByText(
                "ExampleServiceTest.kt",
                """
                package example

                class ExampleServiceTest
                """.trimIndent(),
            ) as KtFile
        val ktClass = psiFile.declarations.filterIsInstance<KtClass>().single()
        val lightClass = ktClass.toLightClass()!!

        assertEquals(ktClass, ArmeriaJUnitServerExtensionSupport.toKtClass(lightClass))
    }

    fun testInsertsJavaTestMethodForServerExtensionClass() {
        val javaFile =
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
            ) as PsiJavaFile
        myFixture.openFileInEditor(javaFile.virtualFile)

        val inserted =
            ArmeriaTestMethodInserter.insertFromRouteExplorer(
                project,
                route(path = "/api"),
            )
        assertTrue(inserted)
        repeat(5) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        }

        val testClass = javaFile.classes.single()
        val method = testClass.methods.singleOrNull { it.name == "apiReturnsSuccess" }
        kotlinAssertNotNull(method)
        assertTrue(method.text.contains("WebClient.of"))
    }

    fun testInsertsKotlinTestMethodIntoServerExtensionClass() {
        val psiFile =
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
            ) as KtFile
        val ktClass = psiFile.declarations.filterIsInstance<KtClass>().single()
        myFixture.openFileInEditor(psiFile.virtualFile)

        WriteCommandAction.runWriteCommandAction(
            project,
            {
                ArmeriaTestMethodInserter.insertKotlinMethod(
                    project,
                    ktClass,
                    ArmeriaTestMethodGenerator.generateTestMethod(
                        route(path = "/api"),
                        serverVariableName = "server",
                        language = ArmeriaTestLanguage.KOTLIN,
                    ),
                )
            },
        )

        val function =
            ktClass.declarations.filterIsInstance<KtNamedFunction>().singleOrNull { it.name == "apiReturnsSuccess" }
        kotlinAssertNotNull(function)
        assertTrue(function.text.contains("WebClient.of"))
    }

    fun testDoesNotTargetFirstClassInMultiClassKotlinFile() {
        val psiFile =
            myFixture.configureByText(
                "ExampleServiceTest.kt",
                """
                package example

                import org.junit.jupiter.api.extension.RegisterExtension
                import com.linecorp.armeria.testing.junit5.server.ServerExtension

                class OtherTest {
                    @RegisterExtension
                    val server: ServerExtension = object : ServerExtension() {}
                }

                class ExampleServiceTest
                """.trimIndent(),
            ) as KtFile
        myFixture.openFileInEditor(psiFile.virtualFile)

        val resolved =
            ArmeriaTestMethodInserter.resolveTargetClassInternal(
                project,
                route(path = "/api", moduleName = "unmatched-module"),
            )

        assertNull(resolved)
    }

    fun testModuleFallbackWhenEmptyTestFileOpen() {
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
        val emptyTestFile =
            myFixture.configureByText(
                "EmptyTest.java",
                """
                package example;
                """.trimIndent(),
            )
        myFixture.openFileInEditor(emptyTestFile.virtualFile)

        val resolved =
            ArmeriaTestMethodInserter.resolveTargetClassInternal(
                project,
                route(path = "/api"),
            )

        assertEquals("ExampleServiceTest", resolved?.name)
    }

    fun testModuleFallbackWhenNoEditorOpen() {
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
        val fileEditorManager = FileEditorManager.getInstance(project)
        fileEditorManager.openFiles.toList().forEach { fileEditorManager.closeFile(it) }

        val resolved =
            ArmeriaTestMethodInserter.resolveTargetClassInternal(
                project,
                route(path = "/api"),
            )

        assertEquals("ExampleServiceTest", resolved?.name)
    }

    fun testDoesNotModuleFallbackWhenMainSourceEditorFocused() {
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

        val mainRoot = myFixture.tempDirFixture.findOrCreateDir("main")
        try {
            PsiTestUtil.addSourceRoot(module, mainRoot, false)
            val virtualFile =
                ApplicationManager.getApplication().runWriteAction<VirtualFile> {
                    val file = mainRoot.createChildData(this, "ExampleService.java")
                    VfsUtil.saveText(
                        file,
                        """
                        package example;

                        public class ExampleService {}
                        """.trimIndent(),
                    )
                    file
                }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            myFixture.openFileInEditor(virtualFile)

            val resolved =
                ArmeriaTestMethodInserter.resolveTargetClassInternal(
                    project,
                    route(path = "/api"),
                )

            assertNull(resolved)
        } finally {
            PsiTestUtil.removeSourceRoot(module, mainRoot)
        }
    }

    fun testDoesNotModuleFallbackWhenDifferentTestClassFocused() {
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
        val otherTestFile =
            myFixture.configureByText(
                "OtherTest.java",
                """
                package example;

                public class OtherTest {}
                """.trimIndent(),
            )
        myFixture.openFileInEditor(otherTestFile.virtualFile)

        val resolved =
            ArmeriaTestMethodInserter.resolveTargetClassInternal(
                project,
                route(path = "/api"),
            )

        assertNull(resolved)
    }

    fun testDoesNotModuleFallbackWhenMainSourceKotlinEditorFocused() {
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

        val mainRoot = myFixture.tempDirFixture.findOrCreateDir("main")
        try {
            PsiTestUtil.addSourceRoot(module, mainRoot, false)
            val virtualFile =
                ApplicationManager.getApplication().runWriteAction<VirtualFile> {
                    val file = mainRoot.createChildData(this, "ExampleService.kt")
                    VfsUtil.saveText(
                        file,
                        """
                        package example

                        class ExampleService
                        """.trimIndent(),
                    )
                    file
                }
            PsiDocumentManager.getInstance(project).commitAllDocuments()
            myFixture.openFileInEditor(virtualFile)

            val resolved =
                ArmeriaTestMethodInserter.resolveTargetClassInternal(
                    project,
                    route(path = "/api"),
                )

            assertNull(resolved)
        } finally {
            PsiTestUtil.removeSourceRoot(module, mainRoot)
        }
    }

    fun testDoesNotModuleFallbackWhenDifferentKotlinTestClassFocused() {
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
        val otherTestFile =
            myFixture.configureByText(
                "OtherTest.kt",
                """
                package example

                class OtherTest
                """.trimIndent(),
            ) as KtFile
        myFixture.openFileInEditor(otherTestFile.virtualFile)

        val resolved =
            ArmeriaTestMethodInserter.resolveTargetClassInternal(
                project,
                route(path = "/api"),
            )

        assertNull(resolved)
    }

    fun testResolvesInheritedServerExtensionInSubclass() {
        val javaFile =
            myFixture.configureByText(
                "InheritedServerInserterTest.java",
                """
                package example;

                import org.junit.jupiter.api.extension.RegisterExtension;
                import com.linecorp.armeria.testing.junit5.server.ServerExtension;

                abstract class InserterBaseTest {
                    @RegisterExtension
                    static ServerExtension server = new ServerExtension() {};
                }

                public class InheritedServerInserterTest extends InserterBaseTest {
                }
                """.trimIndent(),
            ) as PsiJavaFile
        val testClass = javaFile.classes.single { it.name == "InheritedServerInserterTest" }
        myFixture.openFileInEditor(javaFile.virtualFile)
        myFixture.editor.caretModel.moveToOffset(testClass.textRange.startOffset)

        val extensions = ArmeriaJUnitServerExtensionCollector.extensionsInClass(project, testClass)
        assertEquals(1, extensions.size)

        val resolved =
            ArmeriaTestMethodInserter.resolveTargetClassInternal(
                project,
                route(path = "/api"),
            )
        assertEquals("InheritedServerInserterTest", resolved?.name)
    }

    private fun route(
        path: String,
        moduleName: String = module.name,
    ): ArmeriaRoute =
        ArmeriaRoute(
            protocol = "HTTP",
            httpMethod = "GET",
            path = path,
            target = "Handler",
            routeMatch = RouteMatch.ANNOTATED_HTTP,
            moduleName = moduleName,
            targetUnresolved = false,
            isDocService = false,
            decorators = emptyList(),
            exceptionHandlers = emptyList(),
            executionHints = emptyList(),
            pointer = EmptyPointer,
        )

    private object EmptyPointer : SmartPsiElementPointer<PsiElement> {
        override fun getElement(): PsiElement? = null

        override fun getContainingFile(): PsiFile? = null

        override fun getRange(): TextRange? = null

        override fun getProject() = throw UnsupportedOperationException()

        override fun getVirtualFile(): VirtualFile = throw UnsupportedOperationException()

        override fun getPsiRange(): TextRange? = null
    }
}
