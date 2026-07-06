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
}
