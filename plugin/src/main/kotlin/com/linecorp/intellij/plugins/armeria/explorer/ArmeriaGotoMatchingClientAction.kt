package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientRouteNavigation
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.message

internal class ArmeriaGotoMatchingClientAction(
    private val selectedRouteProvider: () -> ArmeriaRoute?,
) : DumbAwareAction(
        message("route.explorer.action.gotoClient"),
    ) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null && selectedRouteProvider() != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val route = selectedRouteProvider() ?: return
        ArmeriaClientRouteNavigation.openMatchingClients(project, route)
    }
}
