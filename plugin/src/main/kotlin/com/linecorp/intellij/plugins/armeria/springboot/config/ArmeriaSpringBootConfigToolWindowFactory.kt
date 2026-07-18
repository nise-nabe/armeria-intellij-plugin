package com.linecorp.intellij.plugins.armeria.springboot.config

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentFactory

class ArmeriaSpringBootConfigToolWindowFactory :
    ToolWindowFactory,
    DumbAware {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val panel = ArmeriaSpringBootConfigPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        Disposer.register(content, panel)
        toolWindow.contentManager.addContent(content)
        project.messageBus.connect(content).subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun toolWindowShown(w: ToolWindow) {
                    if (w.id == toolWindow.id) panel.scheduleInitialRefreshIfNeeded()
                }
            },
        )
        if (toolWindow.isVisible) panel.scheduleInitialRefreshIfNeeded()
    }

    override fun shouldBeAvailable(project: Project) = true
}
