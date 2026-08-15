package com.linecorp.intellij.plugins.armeria

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.NonBlockingReadAction
import com.intellij.openapi.util.Disposer

/**
 * Plugin-scoped [Disposable] cancelled when this plugin is unloaded without an IDE restart.
 *
 * After [dispose], [get] returns a throwaway already-disposed token so in-flight callers
 * cannot register a new Application child on the outgoing classloader.
 */
object ArmeriaPluginDisposable {
    private val lock = Any()

    @Volatile
    private var instance: Disposable? = null

    @Volatile
    private var closed = false

    private var unloadedToken: Disposable? = null

    fun get(): Disposable {
        if (!closed) {
            instance?.let { return it }
        }
        return synchronized(lock) {
            if (closed) {
                closedDisposable()
            } else {
                instance ?: newInstance()
            }
        }
    }

    fun dispose() {
        synchronized(lock) {
            closed = true
            val current = instance
            instance = null
            if (current != null) {
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

    private fun closedDisposable(): Disposable {
        unloadedToken?.let {
            return it
        }
        val token = Disposer.newDisposable("Armeria plugin (unloaded)")
        Disposer.dispose(token)
        unloadedToken = token
        return token
    }
}

fun <T> NonBlockingReadAction<T>.expireWithPluginUnload(): NonBlockingReadAction<T> = expireWith(ArmeriaPluginDisposable.get())
