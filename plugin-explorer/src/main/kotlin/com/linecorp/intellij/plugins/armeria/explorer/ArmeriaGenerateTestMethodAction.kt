package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaTestMethodGeneration
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaGenerateTestMethodAction(
    private val selectedRouteProvider: () -> ArmeriaRoute?,
    private val testMethodGeneration: ArmeriaTestMethodGeneration = service(),
) : DumbAwareAction(
        message("route.explorer.action.generateTestMethod"),
    ) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null || project.basePath == null) {
            e.presentation.isEnabled = false
            return
        }
        val route = selectedRouteProvider()
        e.presentation.isEnabled = route != null && testMethodGeneration.supports(route)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val route = selectedRouteProvider() ?: return
        if (!testMethodGeneration.supports(route)) {
            return
        }
        testMethodGeneration.insertFromRouteExplorerAsync(project, route)
    }
}
