package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.PlatformTestUtil
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

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
        assertNotNull(method)
        assertTrue(method!!.text.contains("WebClient.of"))
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
        assertNotNull(function)
        assertTrue(function!!.text.contains("WebClient.of"))
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

    private fun route(
        path: String,
        moduleName: String = "app",
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
