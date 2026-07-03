package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.linecorp.intellij.plugins.armeria.message
import java.nio.charset.StandardCharsets
import java.nio.file.Path

internal object ArmeriaHttpRequestFileWriter {
    private val LOG = logger<ArmeriaHttpRequestFileWriter>()
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
                    try {
                        val parentDir = filePath.parent.toFile()
                        when {
                            parentDir.exists() && !parentDir.isDirectory -> return@runWriteCommandAction
                            !parentDir.exists() && !parentDir.mkdirs() -> return@runWriteCommandAction
                        }
                        val parent = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(parentDir)
                            ?: return@runWriteCommandAction
                        if (!parent.isDirectory) {
                            return@runWriteCommandAction
                        }
                        val existing = parent.findChild(fileName)
                        if (existing != null && existing.isDirectory) {
                            return@runWriteCommandAction
                        }
                        val virtualFile = existing ?: parent.createChildData(this, fileName)
                        virtualFile.setBinaryContent(content.toByteArray(StandardCharsets.UTF_8))
                        FileEditorManager.getInstance(project).openFile(virtualFile, true)
                    } catch (e: Exception) {
                        LOG.warn("Failed to create or update HTTP request file: $fileName", e)
                    }
                },
            )
        }
    }
}
