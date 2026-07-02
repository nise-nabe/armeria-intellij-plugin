package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaSyncRuntimeRoutesAction : DumbAwareAction(
    message("route.explorer.action.syncRuntime"),
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val host = Messages.showInputDialog(
            project,
            message("route.explorer.sync.host.prompt"),
            message("route.explorer.action.syncRuntime"),
            Messages.getQuestionIcon(),
            "localhost",
            null,
        ) ?: return
        val portText = Messages.showInputDialog(
            project,
            message("route.explorer.sync.port.prompt"),
            message("route.explorer.action.syncRuntime"),
            Messages.getQuestionIcon(),
            "8080",
            null,
        ) ?: return
        val port = portText.toIntOrNull() ?: 8080
        val routes = ArmeriaRuntimeRouteFetcher.fetch(host, port)
        if (routes.isEmpty()) {
            Messages.showWarningDialog(
                project,
                message("route.explorer.sync.empty", host, port),
                message("route.explorer.action.syncRuntime"),
            )
            return
        }
        val summary = routes.joinToString("\n") { "${it.method} ${it.path}" }
        Messages.showInfoMessage(
            project,
            summary,
            message("route.explorer.sync.result", routes.size),
        )
    }
}
