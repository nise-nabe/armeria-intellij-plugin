package com.linecorp.intellij.plugins.armeria

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.NonBlockingReadAction
import com.intellij.openapi.util.Disposer

/**
 * Plugin-scoped [Disposable] cancelled when this plugin is unloaded without an IDE restart.
 */
object ArmeriaPluginDisposable {
    private val lock = Any()

    @Volatile
    private var instance: Disposable? = null

    fun get(): Disposable {
        instance?.takeUnless { Disposer.isDisposed(it) }?.let { return it }
        return synchronized(lock) {
            instance?.takeUnless { Disposer.isDisposed(it) } ?: newInstance()
        }
    }

    fun dispose() {
        synchronized(lock) {
            val current = instance
            instance = null
            if (current != null && !Disposer.isDisposed(current)) {
                Disposer.dispose(current)
            }
        }
    }

    private fun newInstance(): Disposable {
        val created = Disposer.newDisposable("Armeria plugin")
        Disposer.register(ApplicationManager.getApplication(), created)
        instance = created
        return created
    }
}

fun <T> NonBlockingReadAction<T>.expireWithPluginUnload(): NonBlockingReadAction<T> = expireWith(ArmeriaPluginDisposable.get())
