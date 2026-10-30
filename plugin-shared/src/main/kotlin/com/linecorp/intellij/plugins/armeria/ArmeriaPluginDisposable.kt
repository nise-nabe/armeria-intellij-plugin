package com.linecorp.intellij.plugins.armeria

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.NonBlockingReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.CheckedDisposable

/**
 * Plugin-scoped [Disposable] cancelled when this plugin is unloaded without an IDE restart.
 *
 * Registered as a light application service so the platform disposes it on dynamic unload.
 * Do not parent plugin work to [ApplicationManager.getApplication] — that is not disposed
 * when the plugin unloads.
 *
 * Unload checks use [CheckedDisposable.isDisposed]. [com.intellij.openapi.util.Disposer.isDisposed]
 * is deprecated: its answer is diagnostic state that the platform drops after plugin unload.
 */
@Service(Service.Level.APP)
class ArmeriaPluginDisposable : CheckedDisposable {
    @Volatile
    private var disposed = false

    override fun isDisposed(): Boolean = disposed

    override fun dispose() {
        disposed = true
    }
}

fun pluginUnloadDisposable(): CheckedDisposable = ApplicationManager.getApplication().getService(ArmeriaPluginDisposable::class.java)

fun <T> NonBlockingReadAction<T>.expireWithPluginUnload(): NonBlockingReadAction<T> = expireWith(pluginUnloadDisposable())
