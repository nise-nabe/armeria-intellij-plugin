package com.linecorp.intellij.plugins.armeria.debugger

import com.intellij.debugger.ui.tree.render.CompoundRendererProvider
import com.intellij.debugger.ui.tree.render.CompoundReferenceRenderer
import com.intellij.debugger.ui.tree.render.EnumerationChildrenRenderer
import com.intellij.debugger.ui.tree.render.LabelRenderer
import com.intellij.ide.highlighter.JavaFileType
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.CHILD_ID
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.CHILD_METHOD
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.CHILD_PATH
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.CHILD_REMOTE_ADDRESS
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.CHILD_REQUEST_TIMEOUT
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.CHILD_RESPONSE_TIMEOUT
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.CLIENT_REQUEST_CONTEXT_CLASS
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.LABEL_EXPRESSION
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.SERVICE_REQUEST_CONTEXT_CLASS
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.test.ArmeriaLightJavaCodeInsightFixtureTestCase
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ArmeriaRequestContextRendererProviderTest : ArmeriaLightJavaCodeInsightFixtureTestCase() {
    fun testProvidersRegisteredOnExtensionPoint() {
        val providers = CompoundRendererProvider.EP_NAME.extensionList
        assertNotNull(providers.find { it is ArmeriaServiceRequestContextRendererProvider })
        assertNotNull(providers.find { it is ArmeriaClientRequestContextRendererProvider })
    }

    fun testServiceRequestContextRendererConfiguration() {
        assertRenderer(
            provider = ArmeriaServiceRequestContextRendererProvider(),
            expectedName = message("debugger.service.request.context.renderer.name"),
            expectedClassName = SERVICE_REQUEST_CONTEXT_CLASS,
            timeoutChildName = CHILD_REQUEST_TIMEOUT,
            timeoutExpression = "requestTimeoutMillis()",
        )
    }

    fun testClientRequestContextRendererConfiguration() {
        assertRenderer(
            provider = ArmeriaClientRequestContextRendererProvider(),
            expectedName = message("debugger.client.request.context.renderer.name"),
            expectedClassName = CLIENT_REQUEST_CONTEXT_CLASS,
            timeoutChildName = CHILD_RESPONSE_TIMEOUT,
            timeoutExpression = "responseTimeoutMillis()",
        )
    }

    fun testClassNamesArePlainStringsWithoutArmeriaClasspathTypes() {
        // Providers must target Armeria only via FQCN strings (no compile-time Armeria types).
        assertEquals(
            "com.linecorp.armeria.server.ServiceRequestContext",
            SERVICE_REQUEST_CONTEXT_CLASS,
        )
        assertEquals(
            "com.linecorp.armeria.client.ClientRequestContext",
            CLIENT_REQUEST_CONTEXT_CLASS,
        )
        val service = assertIs<CompoundReferenceRenderer>(
            ArmeriaServiceRequestContextRendererProvider().createRenderer(),
        )
        val client = assertIs<CompoundReferenceRenderer>(
            ArmeriaClientRequestContextRendererProvider().createRenderer(),
        )
        assertEquals(SERVICE_REQUEST_CONTEXT_CLASS, service.className)
        assertEquals(CLIENT_REQUEST_CONTEXT_CLASS, client.className)
    }

    private fun assertRenderer(
        provider: ArmeriaRequestContextRendererProvider,
        expectedName: String,
        expectedClassName: String,
        timeoutChildName: String,
        timeoutExpression: String,
    ) {
        assertEquals(expectedName, provider.createRenderer().name)
        val renderer = assertIs<CompoundReferenceRenderer>(provider.createRenderer())
        assertEquals(expectedClassName, renderer.className)
        assertTrue(renderer.isEnabled)

        val labelRenderer = assertIs<LabelRenderer>(renderer.labelRenderer)
        assertEquals(LABEL_EXPRESSION, labelRenderer.labelExpression.text)
        assertEquals(JavaFileType.INSTANCE, labelRenderer.labelExpression.fileType)

        val childrenRenderer = assertIs<EnumerationChildrenRenderer>(renderer.childrenRenderer)
        assertTrue(childrenRenderer.isAppendDefaultChildren)
        val children = childrenRenderer.children.associate { it.myName to it.myExpression.text }
        assertEquals("method()", children[CHILD_METHOD])
        assertEquals("path()", children[CHILD_PATH])
        assertEquals("id()", children[CHILD_ID])
        assertEquals("remoteAddress()", children[CHILD_REMOTE_ADDRESS])
        assertEquals(timeoutExpression, children[timeoutChildName])
    }
}
