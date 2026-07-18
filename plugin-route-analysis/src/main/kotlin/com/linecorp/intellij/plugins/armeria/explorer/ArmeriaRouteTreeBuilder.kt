package com.linecorp.intellij.plugins.armeria.explorer

import javax.swing.tree.DefaultMutableTreeNode

object ArmeriaRouteTreeBuilder {
    fun buildRoot(routes: List<ArmeriaRoute>): DefaultMutableTreeNode {
        val root = DefaultMutableTreeNode(RootNode)
        if (routes.isEmpty()) {
            return root
        }
        for ((moduleName, moduleRoutes) in routes.groupBy { it.moduleName }.toSortedMap()) {
            val moduleNode = DefaultMutableTreeNode(ModuleNode(moduleName, moduleRoutes.size))
            for (route in moduleRoutes) {
                moduleNode.add(DefaultMutableTreeNode(RouteNode(route)))
            }
            root.add(moduleNode)
        }
        return root
    }

    fun selectedRoute(node: Any?): ArmeriaRoute? =
        when (val userObject = (node as? DefaultMutableTreeNode)?.userObject) {
            is RouteNode -> userObject.route
            else -> null
        }

    data object RootNode

    data class ModuleNode(
        val name: String,
        val routeCount: Int,
    )

    data class RouteNode(
        val route: ArmeriaRoute,
    )
}
