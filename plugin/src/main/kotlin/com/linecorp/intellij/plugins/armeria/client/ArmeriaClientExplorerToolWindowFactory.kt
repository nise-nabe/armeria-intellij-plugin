package com.linecorp.intellij.plugins.armeria.client

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentFactory

class ArmeriaClientExplorerToolWindowFactory :
    ToolWindowFactory,
    DumbAware {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val panel = ArmeriaClientExplorerPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        Disposer.register(content, panel)
        toolWindow.contentManager.addContent(content)

        val connection = project.messageBus.connect(content)
        connection.subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun toolWindowShown(shownToolWindow: ToolWindow) {
                    if (shownToolWindow.id == toolWindow.id) {
                        panel.scheduleInitialRefreshIfNeeded()
                    }
                }
            },
        )
        if (toolWindow.isVisible) {
            panel.scheduleInitialRefreshIfNeeded()
        }
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
