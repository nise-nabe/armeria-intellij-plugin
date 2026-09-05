package com.linecorp.intellij.plugins.armeria.debugger

import com.intellij.debugger.ui.tree.render.CompoundRendererProvider
import com.intellij.debugger.ui.tree.render.CompoundReferenceRenderer
import com.intellij.debugger.ui.tree.render.EnumerationChildrenRenderer
import com.intellij.debugger.ui.tree.render.LabelRenderer
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.CHILD_ID
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.CHILD_METHOD
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.CHILD_PATH
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.CHILD_REMOTE_ADDRESS
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.CHILD_REQUEST_TIMEOUT
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.CHILD_RESPONSE_TIMEOUT
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.CLIENT_REQUEST_CONTEXT_CLASS
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.LABEL_EXPRESSION
import com.linecorp.intellij.plugins.armeria.debugger.ArmeriaRequestContextRendererProvider.Companion.SERVICE_REQUEST_CONTEXT_CLASS
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
            expectedClassName = SERVICE_REQUEST_CONTEXT_CLASS,
            timeoutChildName = CHILD_REQUEST_TIMEOUT,
            timeoutExpression = "requestTimeoutMillis()",
        )
    }

    fun testClientRequestContextRendererConfiguration() {
        assertRenderer(
            provider = ArmeriaClientRequestContextRendererProvider(),
            expectedClassName = CLIENT_REQUEST_CONTEXT_CLASS,
            timeoutChildName = CHILD_RESPONSE_TIMEOUT,
            timeoutExpression = "responseTimeoutMillis()",
        )
    }

    fun testProvidersDoNotLoadArmeriaClasses() {
        val before = loadedArmeriaClassNames()
        ArmeriaServiceRequestContextRendererProvider().createRenderer()
        ArmeriaClientRequestContextRendererProvider().createRenderer()
        val after = loadedArmeriaClassNames()
        assertEquals(before, after, "Creating renderers must not load Armeria classes into the IDE process")
    }

    private fun assertRenderer(
        provider: ArmeriaRequestContextRendererProvider,
        expectedClassName: String,
        timeoutChildName: String,
        timeoutExpression: String,
    ) {
        val renderer = assertIs<CompoundReferenceRenderer>(provider.createRenderer())
        assertEquals(expectedClassName, renderer.className)
        assertTrue(renderer.isEnabled)

        val labelRenderer = assertIs<LabelRenderer>(renderer.labelRenderer)
        assertEquals(LABEL_EXPRESSION, labelRenderer.labelExpression.text)

        val childrenRenderer = assertIs<EnumerationChildrenRenderer>(renderer.childrenRenderer)
        assertTrue(childrenRenderer.isAppendDefaultChildren)
        val children = childrenRenderer.children.associate { it.myName to it.myExpression.text }
        assertEquals("method()", children[CHILD_METHOD])
        assertEquals("path()", children[CHILD_PATH])
        assertEquals("id()", children[CHILD_ID])
        assertEquals("remoteAddress()", children[CHILD_REMOTE_ADDRESS])
        assertEquals(timeoutExpression, children[timeoutChildName])
    }

    private fun loadedArmeriaClassNames(): Set<String> =
        buildSet {
            var loader: ClassLoader? = javaClass.classLoader
            while (loader != null) {
                if (loader is java.net.URLClassLoader) {
                    // URLClassLoader does not expose loaded classes; fall through to Instrumentation-free check below.
                }
                loader = loader.parent
            }
            // Inspect classes already defined in this test ClassLoader via reflection on ClassLoader.findLoadedClass.
            val findLoaded =
                ClassLoader::class.java.getDeclaredMethod("findLoadedClass", String::class.java).apply {
                    isAccessible = true
                }
            for (name in listOf(
                SERVICE_REQUEST_CONTEXT_CLASS,
                CLIENT_REQUEST_CONTEXT_CLASS,
                "com.linecorp.armeria.common.RequestContext",
            )) {
                if (findLoaded.invoke(javaClass.classLoader, name) != null) {
                    add(name)
                }
            }
        }
}
