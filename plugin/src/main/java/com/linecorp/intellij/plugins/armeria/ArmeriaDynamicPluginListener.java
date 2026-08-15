package com.linecorp.intellij.plugins.armeria;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.openapi.extensions.PluginId;
import com.linecorp.intellij.plugins.armeria.explorer.collector.ArmeriaRouteCollector;
import org.jetbrains.annotations.NotNull;

/**
 * Releases plugin-scoped work before this plugin's classloader is discarded on a dynamic unload.
 *
 * <p>Implemented in Java so the bytecode does not synthesize Kotlin bridges for deprecated
 * {@link DynamicPluginListener} default methods ({@code checkUnloadPlugin}, experimental
 * load hooks). Those bridges fail Plugin Verifier even when the Kotlin source does not
 * override them.
 */
public final class ArmeriaDynamicPluginListener implements DynamicPluginListener {
    @Override
    public void beforePluginUnload(@NotNull IdeaPluginDescriptor pluginDescriptor, boolean isUpdate) {
        if (!PluginId.getId(ArmeriaPluginIdKt.ARMERIA_PLUGIN_ID).equals(pluginDescriptor.getPluginId())) {
            return;
        }
        ArmeriaPluginDisposable.INSTANCE.dispose();
        ArmeriaRouteCollector.INSTANCE.clearCacheKeys();
    }
}
