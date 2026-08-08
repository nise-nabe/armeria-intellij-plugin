package com.linecorp.intellij.plugins.armeria.test

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaGenerateTestMethodAction
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaGenerateTestMethodActionTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.registerArmeriaJUnitTestSupportStubs()
    }

    fun testActionDisabledWhenRouteIsNull() {
        val action = ArmeriaGenerateTestMethodAction { null }
        val event = TestActionEvent.createTestEvent(action)
        ActionUtil.updateAction(action, event)
        assertFalse(event.presentation.isEnabled)
    }

    fun testActionDisabledForUnsupportedRouteType() {
        val action = ArmeriaGenerateTestMethodAction { route(path = "/api", routeMatch = RouteMatch.SERVICE, httpMethod = "") }
        val event = TestActionEvent.createTestEvent(action)
        ActionUtil.updateAction(action, event)
        assertFalse(event.presentation.isEnabled)
    }

    fun testActionEnabledForAnnotatedHttpRoute() {
        val action = ArmeriaGenerateTestMethodAction { route(path = "/api") }
        val event = TestActionEvent.createTestEvent(action)
        ActionUtil.updateAction(action, event)
        assertTrue(event.presentation.isEnabled)
    }

    fun testActionPerformedInsertsJavaTestMethod() {
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

        val action = ArmeriaGenerateTestMethodAction { route(path = "/api") }
        val presentation = myFixture.testAction(action)
        assertTrue(presentation.isEnabled)
        repeat(5) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        }

        val method =
            javaFile.classes
                .single()
                .methods
                .single { it.name == "apiReturnsSuccess" }
        assertTrue(method.text.contains("WebClient.of"))
    }

    fun testActionPerformedDoesNotInsertWhenExtensionsAreAmbiguous() {
        val javaFile =
            myFixture.configureByText(
                "AmbiguousTest.java",
                """
                package example;

                import org.junit.jupiter.api.extension.RegisterExtension;
                import com.linecorp.armeria.testing.junit5.server.ServerExtension;

                public class AmbiguousTest {
                    @RegisterExtension
                    static ServerExtension server1 = new ServerExtension() {};

                    @RegisterExtension
                    static ServerExtension server2 = new ServerExtension() {};
                }
                """.trimIndent(),
            ) as PsiJavaFile
        myFixture.openFileInEditor(javaFile.virtualFile)

        val action = ArmeriaGenerateTestMethodAction { route(path = "/api") }
        assertFailsWith<RuntimeException> {
            myFixture.testAction(action)
        }
        repeat(5) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        }

        assertTrue(
            javaFile.classes
                .single()
                .methods
                .none { it.name == "apiReturnsSuccess" },
        )
    }

    private fun route(
        path: String,
        routeMatch: RouteMatch = RouteMatch.ANNOTATED_HTTP,
        httpMethod: String = "GET",
    ): ArmeriaRoute =
        ArmeriaRoute(
            protocol = "HTTP",
            httpMethod = httpMethod,
            path = path,
            target = "Handler",
            routeMatch = routeMatch,
            moduleName = module.name,
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
