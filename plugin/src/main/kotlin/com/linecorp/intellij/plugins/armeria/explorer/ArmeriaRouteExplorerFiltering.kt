package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.linecorp.intellij.plugins.armeria.message
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.TreePath

internal object ArmeriaRouteExplorerFiltering {
    fun filterRoutes(
        project: Project,
        routes: List<ArmeriaRoute>,
        currentModuleOnly: Boolean,
    ): List<ArmeriaRoute> {
        val withoutRuntime = routes.filter { it.routeMatch != RouteMatch.RUNTIME }
        val runtimeOnly = routes.filter { it.routeMatch == RouteMatch.RUNTIME }
        if (!currentModuleOnly) {
            return withoutRuntime + runtimeOnly
        }
        val selectedModule = selectedEditorModule(project) ?: return runtimeOnly
        return withoutRuntime.filter { it.moduleName == selectedModule.name } + runtimeOnly
    }

    fun selectedEditorModule(project: Project): com.intellij.openapi.module.Module? {
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() ?: return null
        return ModuleUtilCore.findModuleForFile(file, project)
    }

    fun summary(collectedRoutes: List<ArmeriaRoute>): String {
        if (collectedRoutes.isEmpty()) {
            return message("route.explorer.summary.empty")
        }
        val docServiceDetected = collectedRoutes.any { it.isDocService }
        val runtimeCount = collectedRoutes.count { it.routeMatch == RouteMatch.RUNTIME }
        return buildString {
            append(message("route.explorer.summary.routes", collectedRoutes.size))
            append(" · ")
            append(
                collectedRoutes.groupBy { it.protocol }.entries.joinToString {
                    message("route.explorer.summary.protocolBreakdown", it.key, it.value.size)
                },
            )
            if (docServiceDetected) {
                append(" · ")
                append(message("route.explorer.summary.docService"))
            }
            if (runtimeCount > 0) {
                append(" · ")
                append(message("route.explorer.summary.runtime", runtimeCount))
            }
            val observability = ArmeriaObservabilitySummary.summarize(collectedRoutes)
            if (observability.isNotEmpty()) {
                append(" · ")
                append(observability)
            }
        }
    }

    fun restoreTreeSelection(
        routeTree: javax.swing.JTree,
        root: DefaultMutableTreeNode,
        route: ArmeriaRoute,
    ): Boolean {
        for (moduleIndex in 0 until root.childCount) {
            val moduleNode = root.getChildAt(moduleIndex) as DefaultMutableTreeNode
            val module = moduleNode.userObject as? ArmeriaRouteTreeBuilder.ModuleNode ?: continue
            if (module.name != route.moduleName) {
                continue
            }
            for (routeIndex in 0 until moduleNode.childCount) {
                val routeNode = moduleNode.getChildAt(routeIndex) as DefaultMutableTreeNode
                val visibleRoute = (routeNode.userObject as? ArmeriaRouteTreeBuilder.RouteNode)?.route ?: continue
                if (routesMatch(visibleRoute, route)) {
                    routeTree.selectionPath = TreePath(routeNode.path)
                    return true
                }
            }
        }
        return false
    }

    private fun routesMatch(left: ArmeriaRoute, right: ArmeriaRoute): Boolean {
        return left.moduleName == right.moduleName &&
            left.path == right.path &&
            left.target == right.target &&
            left.routeMatch == right.routeMatch &&
            left.httpMethod == right.httpMethod &&
            left.virtualHostName == right.virtualHostName
    }
}
