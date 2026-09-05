package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.ui.ArmeriaOpenApiDocumentGenerator
import com.linecorp.intellij.plugins.armeria.message
import java.nio.charset.StandardCharsets
import java.nio.file.Path

internal object ArmeriaOpenApiFileWriter {
    private val LOG = logger<ArmeriaOpenApiFileWriter>()

    fun createOrUpdate(
        project: Project,
        routes: List<ArmeriaRoute>,
    ) {
        val content = ArmeriaOpenApiDocumentGenerator.document(routes)
        val fileName = ArmeriaOpenApiDocumentGenerator.FILE_NAME
        val baseDir = project.basePath ?: return
        val filePath = Path.of(baseDir, ".idea", fileName)
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }
            WriteCommandAction.runWriteCommandAction(
                project,
                message("route.explorer.action.exportOpenApi"),
                null,
                {
                    try {
                        val parentDir = filePath.parent.toFile()
                        when {
                            parentDir.exists() && !parentDir.isDirectory -> {
                                LOG.warn("OpenAPI parent path exists but is not a directory: $parentDir")
                                return@runWriteCommandAction
                            }
                            !parentDir.exists() && !parentDir.mkdirs() -> {
                                LOG.warn("Failed to create OpenAPI directory: $parentDir")
                                return@runWriteCommandAction
                            }
                        }
                        val parent = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(parentDir)
                        if (parent == null) {
                            LOG.warn("Failed to resolve OpenAPI directory in VFS: $parentDir")
                            return@runWriteCommandAction
                        }
                        if (!parent.isDirectory) {
                            LOG.warn("OpenAPI parent path is not a directory in VFS: $parentDir")
                            return@runWriteCommandAction
                        }
                        val existing = parent.findChild(fileName)
                        if (existing != null && existing.isDirectory) {
                            LOG.warn("Cannot create OpenAPI file because path exists as a directory: ${existing.path}")
                            return@runWriteCommandAction
                        }
                        val virtualFile = existing ?: parent.createChildData(this, fileName)
                        virtualFile.setBinaryContent(content.toByteArray(StandardCharsets.UTF_8))
                        FileEditorManager.getInstance(project).openFile(virtualFile, true)
                    } catch (e: ProcessCanceledException) {
                        throw e
                    } catch (e: Exception) {
                        LOG.warn("Failed to create or update OpenAPI file: $fileName", e)
                    }
                },
            )
        }
    }
}
