package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientExplorerPanel
import javax.swing.JComponent

object ArmeriaExplorerAccess {
    const val TOOL_WINDOW_ID = "Armeria"

    fun findRoutePanel(project: Project): ArmeriaRouteExplorerPanel? = findPanel(project, ArmeriaRouteExplorerPanel::class.java)

    fun findClientPanel(project: Project): ArmeriaClientExplorerPanel? = findPanel(project, ArmeriaClientExplorerPanel::class.java)

    /**
     * Invokes [onReady] on the EDT with the Services tab after activating the Armeria tool window
     * and selecting that tab so [ArmeriaExplorerToolWindowFactory] can create content.
     * Passes null when the panel cannot be obtained.
     */
    fun ensureRoutePanel(
        project: Project,
        onReady: (ArmeriaRouteExplorerPanel?) -> Unit,
    ) {
        ensurePanel(project, ArmeriaRouteExplorerPanel::class.java, onReady)
    }

    /**
     * Invokes [onReady] on the EDT with the Clients tab after activating the Armeria tool window
     * and selecting that tab so [ArmeriaExplorerToolWindowFactory] can create content.
     * Passes null when the panel cannot be obtained.
     */
    fun ensureClientPanel(
        project: Project,
        onReady: (ArmeriaClientExplorerPanel?) -> Unit,
    ) {
        ensurePanel(project, ArmeriaClientExplorerPanel::class.java, onReady)
    }

    private fun <T : JComponent> findPanel(
        project: Project,
        type: Class<T>,
    ): T? {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(TOOL_WINDOW_ID) ?: return null
        return toolWindow.contentManager.contents
            .asSequence()
            .mapNotNull { type.castOrNull(it.component) }
            .firstOrNull()
    }

    private fun <T : JComponent> ensurePanel(
        project: Project,
        type: Class<T>,
        onReady: (T?) -> Unit,
    ) {
        if (project.isDisposed) {
            invokeOnEdt { onReady(null) }
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
            invokeOnEdt {
                val panel = findPanel(project, type)
                if (panel != null) {
                    selectContent(toolWindow, panel)
                    scheduleInitialRefresh(panel)
                }
                onReady(panel)
            }
        }, true, false)
    }

    private fun selectContent(
        toolWindow: ToolWindow,
        panel: JComponent,
    ) {
        val content =
            toolWindow.contentManager.contents.firstOrNull { it.component === panel }
                ?: return
        toolWindow.contentManager.setSelectedContent(content, true)
    }

    private fun scheduleInitialRefresh(panel: JComponent) {
        when (panel) {
            is ArmeriaRouteExplorerPanel -> panel.scheduleInitialRefreshIfNeeded()
            is ArmeriaClientExplorerPanel -> panel.scheduleInitialRefreshIfNeeded()
        }
    }

    private fun invokeOnEdt(action: () -> Unit) {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) {
            action()
        } else {
            app.invokeLater(action, ModalityState.any())
        }
    }

    private fun <T> Class<T>.castOrNull(value: Any): T? = if (isInstance(value)) cast(value) else null
}
