package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.linecorp.intellij.plugins.armeria.client.ArmeriaClientEndpoint
import com.linecorp.intellij.plugins.armeria.message

internal class ArmeriaGenerateClientHttpRequestAction(
    private val selectedEndpointProvider: () -> ArmeriaClientEndpoint?,
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
        val endpoint = selectedEndpointProvider()
        e.presentation.isEnabled = endpoint != null && ArmeriaClientHttpRequestSupport.supports(endpoint)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val endpoint = selectedEndpointProvider() ?: return
        if (!ArmeriaClientHttpRequestSupport.supports(endpoint)) {
            return
        }
        val route = ArmeriaClientHttpRequestSupport.toRoute(endpoint) ?: return
        ArmeriaHttpRequestFileWriter.createOrUpdate(
            project,
            route,
            ArmeriaClientHttpRequestSupport.baseUrl(endpoint),
        )
    }
}
