package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.linecorp.intellij.plugins.armeria.message

internal class ArmeriaGenerateHttpRequestAction(
    private val selectedRouteProvider: () -> ArmeriaRoute?,
) : DumbAwareAction(
    message("route.explorer.action.generateHttpRequest"),
) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null || project.basePath == null) {
            e.presentation.isEnabled = false
            return
        }
        val route = selectedRouteProvider()
        e.presentation.isEnabled = route != null && ArmeriaHttpRequestGenerator.supports(route)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val route = selectedRouteProvider() ?: return
        if (!ArmeriaHttpRequestGenerator.supports(route)) {
            return
        }
        ArmeriaHttpRequestFileWriter.createOrUpdate(project, route)
    }
}
