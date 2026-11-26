package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.linecorp.intellij.plugins.armeria.explorer.docservice.ArmeriaDocServiceSupport
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.message
import java.awt.datatransfer.StringSelection

internal class ArmeriaOpenDocServiceDebugFormAction(
    private val selectedRouteProvider: () -> ArmeriaRoute?,
    private val routesProvider: () -> List<ArmeriaRoute>,
    private val lastSyncedBaseUrlProvider: () -> String?,
) : DumbAwareAction(
        message("route.explorer.action.openDocServiceDebugForm"),
    ) {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabled = false
            e.presentation.description = message("route.explorer.action.openDocServiceDebugForm.description")
            return
        }
        val url = resolveUrl()
        e.presentation.isEnabled = url != null
        e.presentation.description =
            if (url != null) {
                message("route.explorer.action.openDocServiceDebugForm.descriptionWithUrl", url)
            } else {
                message("route.explorer.action.openDocServiceDebugForm.description")
            }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val url = resolveUrl() ?: return
        CopyPasteManager.getInstance().setContents(StringSelection(url))
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup("armeria.notifications")
            .createNotification(
                message("route.explorer.docService.debugForm.copied.title"),
                url,
                NotificationType.INFORMATION,
            ).notify(project)
        BrowserUtil.browse(url)
    }

    private fun resolveUrl(): String? =
        ArmeriaDocServiceSupport.debugFormUrl(
            route = selectedRouteProvider(),
            routes = routesProvider(),
            lastSyncedBaseUrl = lastSyncedBaseUrlProvider(),
        )
}
