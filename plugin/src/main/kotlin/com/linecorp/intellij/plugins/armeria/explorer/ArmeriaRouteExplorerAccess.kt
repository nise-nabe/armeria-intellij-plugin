package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowManager

object ArmeriaRouteExplorerAccess {
    const val TOOL_WINDOW_ID = "Armeria Services"

    fun findPanel(project: Project): ArmeriaRouteExplorerPanel? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return null
        return toolWindow.contentManager.contents
            .asSequence()
            .mapNotNull { it.component as? ArmeriaRouteExplorerPanel }
            .firstOrNull()
    }

    /**
     * Returns the Route Explorer panel, activating the Armeria Services tool window first when needed
     * so [ArmeriaRouteExplorerToolWindowFactory] can create content.
     */
    fun ensurePanel(project: Project, onReady: (ArmeriaRouteExplorerPanel?) -> Unit) {
        if (project.isDisposed) {
            onReady(null)
            return
        }
        findPanel(project)?.let {
            onReady(it)
            return
        }
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
        if (toolWindow == null) {
            onReady(null)
            return
        }
        toolWindow.activate({
            if (project.isDisposed) {
                onReady(null)
                return@activate
            }
            onReady(findPanel(project))
        }, true, false)
    }
}
