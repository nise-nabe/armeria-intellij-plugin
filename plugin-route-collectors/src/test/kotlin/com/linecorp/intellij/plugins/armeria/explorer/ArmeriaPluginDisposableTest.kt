package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.application.ApplicationManager
import com.linecorp.intellij.plugins.armeria.ArmeriaPluginDisposable
import com.linecorp.intellij.plugins.armeria.pluginUnloadDisposable
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ArmeriaPluginDisposableTest : ArmeriaFixtureTestBase() {
    fun testApplicationServiceIsRegistered() {
        val service = ApplicationManager.getApplication().getService(ArmeriaPluginDisposable::class.java)
        assertNotNull(service)
        assertSame(service, pluginUnloadDisposable())
        assertFalse(service.isDisposed)

        val standalone = ArmeriaPluginDisposable()
        assertFalse(standalone.isDisposed)
        standalone.dispose()
        assertTrue(standalone.isDisposed)
    }
}
