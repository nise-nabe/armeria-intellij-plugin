package com.linecorp.intellij.plugins.armeria

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.NonBlockingReadAction
import com.intellij.openapi.components.Service

/**
 * Plugin-scoped [Disposable] cancelled when this plugin is unloaded without an IDE restart.
 *
 * Registered as a light application service so the platform disposes it on dynamic unload.
 * Do not parent plugin work to [ApplicationManager.getApplication] — that is not disposed
 * when the plugin unloads.
 *
 * [isDisposed] is the replacement for [com.intellij.openapi.util.Disposer.isDisposed]. That method
 * is deprecated because it reads diagnostic state the platform drops after plugin unload.
 */
@Service(Service.Level.APP)
class ArmeriaPluginDisposable : Disposable {
    @Volatile
    private var disposed = false

    val isDisposed: Boolean
        get() = disposed

    override fun dispose() {
        disposed = true
    }
}

fun pluginUnloadDisposable(): ArmeriaPluginDisposable = ApplicationManager.getApplication().getService(ArmeriaPluginDisposable::class.java)

fun <T> NonBlockingReadAction<T>.expireWithPluginUnload(): NonBlockingReadAction<T> = expireWith(pluginUnloadDisposable())
