package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.linecorp.intellij.plugins.armeria.message

internal class ArmeriaOpenDocServiceAction(
    private val routesProvider: () -> List<ArmeriaRoute>,
) : DumbAwareAction(
        message("route.explorer.action.openDocService"),
    ) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            e.presentation.description = null
            return
        }
        val routes = routesProvider()
        val url = ArmeriaDocServiceSupport.primaryUrl(routes)
        e.presentation.isEnabled = url != null
        e.presentation.description =
            if (url != null) {
                message("route.explorer.action.openDocService.descriptionWithUrl", url)
            } else {
                message("route.explorer.action.openDocService.description")
            }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val url = ArmeriaDocServiceSupport.primaryUrl(routesProvider()) ?: return
        BrowserUtil.browse(url)
    }
}
