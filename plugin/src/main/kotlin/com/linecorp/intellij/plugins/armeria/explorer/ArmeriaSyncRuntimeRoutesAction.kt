package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaSyncRuntimeRoutesAction : DumbAwareAction(
    message("route.explorer.action.syncRuntime"),
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val panel = ArmeriaRouteExplorerAccess.findPanel(project)
        val staticRoutes = panel?.staticRoutes().orEmpty()
        val defaultMountPath = ArmeriaDocServiceMountResolver.candidateMountPaths(staticRoutes, userMountPath = null)
            .firstOrNull()
            .orEmpty()
        val dialog = ArmeriaDocServiceSyncDialog(
            project = project,
            defaultHost = "localhost",
            defaultPort = "8080",
            defaultMountPath = defaultMountPath,
            defaultUseHttps = false,
        )
        if (!dialog.showAndGet()) {
            return
        }
        val port = ArmeriaDocServiceEndpointValidator.validatePort(dialog.portText) ?: return
        val mountPaths = ArmeriaDocServiceMountResolver.candidateMountPaths(
            staticRoutes = staticRoutes,
            userMountPath = dialog.mountPath.takeIf { it.isNotBlank() },
        )
        val request = ArmeriaDocServiceFetchRequest(
            host = dialog.host,
            port = port,
            useHttps = dialog.useHttps,
            mountPaths = mountPaths,
            project = project,
        )
        ProgressManager.getInstance().run(
            object : Task.Backgroundable(project, message("route.explorer.sync.progress"), true) {
                override fun run(indicator: ProgressIndicator) {
                    indicator.isIndeterminate = true
                    val result = try {
                        ArmeriaRuntimeRouteFetcher.fetch(request)
                    } catch (_: ProcessCanceledException) {
                        return
                    }
                    if (indicator.isCanceled || project.isDisposed) {
                        return
                    }
                    ApplicationManager.getApplication().invokeLater({
                        if (project.isDisposed) {
                            return@invokeLater
                        }
                        when (result) {
                            is ArmeriaDocServiceFetchResult.Success -> {
                                ArmeriaRouteExplorerAccess.ensurePanel(project) { explorerPanel ->
                                    if (project.isDisposed) {
                                        return@ensurePanel
                                    }
                                    if (explorerPanel != null) {
                                        explorerPanel.scheduleInitialRefreshIfNeeded()
                                        explorerPanel.applyRuntimeRoutes(result.routes)
                                        Messages.showInfoMessage(
                                            project,
                                            message(
                                                "route.explorer.sync.success",
                                                result.routes.size,
                                                result.specificationUrl,
                                            ),
                                            message("route.explorer.action.syncRuntime"),
                                        )
                                    } else {
                                        Messages.showWarningDialog(
                                            project,
                                            message("route.explorer.sync.panelUnavailable"),
                                            message("route.explorer.action.syncRuntime"),
                                        )
                                    }
                                }
                            }
                            is ArmeriaDocServiceFetchResult.Failure -> {
                                Messages.showWarningDialog(
                                    project,
                                    result.message,
                                    message("route.explorer.action.syncRuntime"),
                                )
                            }
                        }
                    }, ModalityState.any())
                }
            },
        )
    }
}
