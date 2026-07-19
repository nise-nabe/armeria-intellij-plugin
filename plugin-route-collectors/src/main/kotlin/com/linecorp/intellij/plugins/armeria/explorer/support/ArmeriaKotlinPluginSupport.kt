package com.linecorp.intellij.plugins.armeria.explorer.support
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId

/** Detects whether the Kotlin IDEA plugin is loaded in the current IDE process. */
object ArmeriaKotlinPluginSupport {
    private val KOTLIN_PLUGIN_ID = PluginId.getId("org.jetbrains.kotlin")

    fun isKotlinPluginAvailable(): Boolean = PluginManagerCore.isLoaded(KOTLIN_PLUGIN_ID)
}
