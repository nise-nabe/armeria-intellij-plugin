package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientExplorerPanel
import com.linecorp.intellij.plugins.armeria.message
import java.awt.Component

class ArmeriaExplorerToolWindowFactory :
    ToolWindowFactory,
    DumbAware {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val servicesPanel = ArmeriaRouteExplorerPanel(project)
        val clientsPanel = ArmeriaClientExplorerPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val servicesContent =
            contentFactory
                .createContent(
                    servicesPanel,
                    message("armeria.explorer.tab.services"),
                    false,
                ).also { it.isCloseable = false }
        val clientsContent =
            contentFactory
                .createContent(
                    clientsPanel,
                    message("armeria.explorer.tab.clients"),
                    false,
                ).also { it.isCloseable = false }
        Disposer.register(servicesContent, servicesPanel)
        Disposer.register(clientsContent, clientsPanel)

        val contentManager = toolWindow.contentManager
        val selectionListener =
            object : ContentManagerListener {
                override fun selectionChanged(event: ContentManagerEvent) {
                    if (toolWindow.isVisible) {
                        scheduleInitialRefresh(event.content.component)
                    }
                }
            }
        contentManager.addContentManagerListener(selectionListener)
        Disposer.register(toolWindow.disposable) {
            contentManager.removeContentManagerListener(selectionListener)
        }
        contentManager.addContent(servicesContent)
        contentManager.addContent(clientsContent)

        val connection = project.messageBus.connect(toolWindow.disposable)
        connection.subscribe(
            ToolWindowManagerListener.TOPIC,
            object : ToolWindowManagerListener {
                override fun toolWindowShown(shownToolWindow: ToolWindow) {
                    if (shownToolWindow.id == toolWindow.id) {
                        scheduleInitialRefresh(contentManager.selectedContent?.component)
                    }
                }
            },
        )
        if (toolWindow.isVisible) {
            scheduleInitialRefresh(contentManager.selectedContent?.component)
        }
    }

    override fun shouldBeAvailable(project: Project): Boolean = true

    private fun scheduleInitialRefresh(component: Component?) {
        when (component) {
            is ArmeriaRouteExplorerPanel -> component.scheduleInitialRefreshIfNeeded()
            is ArmeriaClientExplorerPanel -> component.scheduleInitialRefreshIfNeeded()
        }
    }
}
