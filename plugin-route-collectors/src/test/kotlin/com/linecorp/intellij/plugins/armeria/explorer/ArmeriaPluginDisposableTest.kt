package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.application.ApplicationManager
import com.linecorp.intellij.plugins.armeria.ArmeriaPluginDisposable
import com.linecorp.intellij.plugins.armeria.pluginUnloadDisposable
import com.linecorp.intellij.plugins.armeria.test.ArmeriaFixtureTestBase
import kotlin.test.assertNotNull
import kotlin.test.assertSame

class ArmeriaPluginDisposableTest : ArmeriaFixtureTestBase() {
    fun testApplicationServiceIsRegistered() {
        val service = ApplicationManager.getApplication().getService(ArmeriaPluginDisposable::class.java)
        assertNotNull(service)
        assertSame(service, pluginUnloadDisposable())
    }
}
