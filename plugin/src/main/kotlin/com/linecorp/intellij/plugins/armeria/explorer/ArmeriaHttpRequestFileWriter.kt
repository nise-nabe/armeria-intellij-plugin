package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.linecorp.intellij.plugins.armeria.message
import java.nio.charset.StandardCharsets
import java.nio.file.Path

internal object ArmeriaHttpRequestFileWriter {
    fun createOrUpdate(project: Project, route: ArmeriaRoute) {
        val content = ArmeriaHttpRequestGenerator.requestText(route)
        val fileName = ArmeriaHttpRequestGenerator.fileName(route)
        val baseDir = project.basePath ?: return
        val filePath = Path.of(baseDir, ".idea", "httpRequests", fileName)
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }
            WriteCommandAction.runWriteCommandAction(
                project,
                message("route.explorer.action.generateHttpRequest"),
                null,
                {
                    val parentDir = filePath.parent.toFile()
                    if (!parentDir.exists()) {
                        parentDir.mkdirs()
                    }
                    val parent = LocalFileSystem.getInstance().refreshAndFindFileByPath(parentDir.path)
                        ?: return@runWriteCommandAction
                    val virtualFile = parent.findChild(fileName)
                        ?: parent.createChildData(this, fileName)
                    virtualFile.setBinaryContent(content.toByteArray(StandardCharsets.UTF_8))
                    FileEditorManager.getInstance(project).openFile(virtualFile, true)
                },
            )
        }
    }
}
