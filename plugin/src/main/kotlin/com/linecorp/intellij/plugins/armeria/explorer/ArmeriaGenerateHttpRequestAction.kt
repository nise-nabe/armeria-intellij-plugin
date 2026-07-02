package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.linecorp.intellij.plugins.armeria.message
import java.nio.file.Path

class ArmeriaGenerateHttpRequestAction : DumbAwareAction(
    message("route.explorer.action.generateHttpRequest"),
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val route = e.getData(ArmeriaRouteDataKeys.SELECTED_ROUTE) ?: return
        if (route.httpMethod.isBlank() && route.routeMatch != RouteMatch.SERVICE) {
            return
        }
        val httpMethod = route.httpMethod.ifBlank { "GET" }
        val requestText = buildString {
            appendLine("### ${route.path}")
            appendLine("$httpMethod http://localhost:8080${route.path}")
            appendLine("Accept: application/json")
            appendLine()
        }
        createHttpFile(project, route.path, requestText)
    }

    private fun createHttpFile(project: Project, path: String, content: String) {
        val fileName = "armeria-${path.trim('/').replace('/', '-')}.http"
        val baseDir = project.basePath ?: return
        val filePath = Path.of(baseDir, ".idea", "httpRequests", fileName)
        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(project) {
                val parentDir = filePath.parent.toFile()
                if (!parentDir.exists()) {
                    parentDir.mkdirs()
                }
                val parent = LocalFileSystem.getInstance().refreshAndFindFileByPath(parentDir.path)
                    ?: return@runWriteCommandAction
                val existing = parent.findChild(fileName)
                if (existing != null) {
                    FileEditorManager.getInstance(project).openFile(existing, true)
                    return@runWriteCommandAction
                }
                val virtualFile = parent.createChildData(this, fileName)
                virtualFile.setBinaryContent(content.toByteArray())
                FileEditorManager.getInstance(project).openFile(virtualFile, true)
            }
        }
    }
}
