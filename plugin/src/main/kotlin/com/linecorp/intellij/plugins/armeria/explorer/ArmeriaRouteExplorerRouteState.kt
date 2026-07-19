package com.linecorp.intellij.plugins.armeria.explorer
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute

/**
 * Static and DocService runtime routes shown in Route Explorer.
 *
 * [runtimeRoutes] survive [applyStatic] (Refresh) until [applyRuntime] replaces them.
 */
internal class ArmeriaRouteExplorerRouteState {
    var staticRoutes: List<ArmeriaRoute> = emptyList()
        private set

    var runtimeRoutes: List<ArmeriaRoute> = emptyList()
        private set

    fun applyStatic(routes: List<ArmeriaRoute>) {
        staticRoutes = routes
    }

    fun applyRuntime(routes: List<ArmeriaRoute>) {
        runtimeRoutes = routes
    }

    fun allRoutes(): List<ArmeriaRoute> = staticRoutes + runtimeRoutes
}
