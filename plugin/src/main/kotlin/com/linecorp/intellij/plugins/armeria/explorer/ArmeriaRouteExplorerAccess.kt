package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
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
     * Invokes [onReady] on the EDT with the Route Explorer panel after optionally activating the
     * Armeria Services tool window so [ArmeriaRouteExplorerToolWindowFactory] can create content.
     * Passes null when the panel cannot be obtained.
     */
    fun ensurePanel(project: Project, onReady: (ArmeriaRouteExplorerPanel?) -> Unit) {
        if (project.isDisposed) {
            invokeOnEdt { onReady(null) }
            return
        }
        findPanel(project)?.let {
            invokeOnEdt { onReady(it) }
            return
        }
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID)
        if (toolWindow == null) {
            invokeOnEdt { onReady(null) }
            return
        }
        toolWindow.activate({
            if (project.isDisposed) {
                invokeOnEdt { onReady(null) }
                return@activate
            }
            invokeOnEdt { onReady(findPanel(project)) }
        }, true, false)
    }

    private fun invokeOnEdt(action: () -> Unit) {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            action()
        } else {
            app.invokeLater(action, ModalityState.any())
        }
    }
}
