package com.linecorp.intellij.plugins.armeria

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector

class ArmeriaDynamicPluginListener : DynamicPluginListener {
    override fun beforePluginUnload(
        pluginDescriptor: IdeaPluginDescriptor,
        isUpdate: Boolean,
    ) {
        if (pluginDescriptor.pluginId != PluginId.getId(ARMERIA_PLUGIN_ID)) {
            return
        }
        ArmeriaPluginDisposable.dispose()
        ArmeriaRouteCollector.clearCacheKeys()
    }
}
