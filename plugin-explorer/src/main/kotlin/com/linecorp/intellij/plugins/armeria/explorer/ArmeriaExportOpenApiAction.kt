package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaOpenApiDocumentGenerator
import com.linecorp.intellij.plugins.armeria.message

internal class ArmeriaExportOpenApiAction(
    private val routesProvider: () -> List<ArmeriaRoute>,
) : DumbAwareAction(
        message("route.explorer.action.exportOpenApi"),
        message("route.explorer.action.exportOpenApi.description"),
        null,
    ) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null || project.basePath == null) {
            e.presentation.isEnabled = false
            return
        }
        e.presentation.isEnabled = routesProvider().any(ArmeriaOpenApiDocumentGenerator::exportable)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (project.basePath == null) {
            return
        }
        val routes = routesProvider()
        if (routes.none(ArmeriaOpenApiDocumentGenerator::exportable)) {
            return
        }
        ArmeriaOpenApiFileWriter.createOrUpdate(project, routes)
    }
}
