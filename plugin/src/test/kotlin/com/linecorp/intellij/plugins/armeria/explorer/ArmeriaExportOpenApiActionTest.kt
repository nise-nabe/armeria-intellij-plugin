package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.TestActionEvent
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteProtocol
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaOpenApiDocumentGenerator
import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase
import java.nio.file.Path
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArmeriaExportOpenApiActionTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    fun testActionDisabledWhenNoRoutes() {
        val action = ArmeriaExportOpenApiAction { emptyList() }
        val event = TestActionEvent.createTestEvent(action)
        ActionUtil.updateAction(action, event)
        assertFalse(event.presentation.isEnabled)
    }

    fun testActionDisabledForThriftOnly() {
        val action =
            ArmeriaExportOpenApiAction {
                listOf(
                    route(
                        protocol = RouteProtocol.THRIFT.presentableName(),
                        httpMethod = "",
                        path = "/HelloService",
                        routeMatch = RouteMatch.NON_HTTP,
                    ),
                )
            }
        val event = TestActionEvent.createTestEvent(action)
        ActionUtil.updateAction(action, event)
        assertFalse(event.presentation.isEnabled)
    }

    fun testActionEnabledForAnnotatedHttpRoute() {
        val action = ArmeriaExportOpenApiAction { listOf(route(path = "/users/{id}")) }
        val event = TestActionEvent.createTestEvent(action)
        ActionUtil.updateAction(action, event)
        assertTrue(event.presentation.isEnabled)
    }

    fun testActionPerformedWritesOpenApiFile() {
        val action = ArmeriaExportOpenApiAction { listOf(route(path = "/users/{id}")) }
        val presentation = myFixture.testAction(action)
        assertTrue(presentation.isEnabled)
        repeat(5) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        }

        val file = Path.of(project.basePath!!, ".idea", ArmeriaOpenApiDocumentGenerator.FILE_NAME).toFile()
        assertTrue(file.isFile, "expected ${file.path} to exist")
        val text = file.readText()
        assertTrue(text.contains("\"/users/{id}\""), text)
        assertTrue(text.contains("name: \"id\""), text)
    }

    private fun route(
        path: String,
        routeMatch: RouteMatch = RouteMatch.ANNOTATED_HTTP,
        httpMethod: String = "GET",
        protocol: String = RouteProtocol.HTTP.presentableName(),
    ): ArmeriaRoute =
        ArmeriaRoute(
            protocol = protocol,
            httpMethod = httpMethod,
            path = path,
            target = "example.UserService#getUser()",
            routeMatch = routeMatch,
            moduleName = module.name,
            targetUnresolved = false,
            isDocService = false,
            decorators = emptyList(),
            exceptionHandlers = emptyList(),
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
