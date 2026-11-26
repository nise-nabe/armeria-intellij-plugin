package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceSupport
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.message

internal class ArmeriaOpenDocServiceAction(
    private val routesProvider: () -> List<ArmeriaRoute>,
    private val lastSyncedBaseUrlProvider: () -> String? = { null },
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
        val url = resolveUrl()
        e.presentation.isEnabled = url != null
        e.presentation.description =
            if (url != null) {
                message("route.explorer.action.openDocService.descriptionWithUrl", url)
            } else {
                message("route.explorer.action.openDocService.description")
            }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val url = resolveUrl() ?: return
        BrowserUtil.browse(url)
    }

    private fun resolveUrl(): String? =
        ArmeriaDocServiceSupport.docsBaseUrl(
            routes = routesProvider(),
            lastSyncedBaseUrl = lastSyncedBaseUrlProvider(),
        )
}
