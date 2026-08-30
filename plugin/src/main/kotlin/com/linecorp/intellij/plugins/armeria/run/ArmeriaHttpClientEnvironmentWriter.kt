package com.linecorp.intellij.plugins.armeria.run

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.impl.LoadTextUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.linecorp.intellij.plugins.armeria.message
import java.nio.charset.StandardCharsets
import java.nio.file.Path

internal object ArmeriaHttpClientEnvironmentWriter {
    private val LOG = logger<ArmeriaHttpClientEnvironmentWriter>()

    fun write(
        project: Project,
        listen: ArmeriaListenEndpoint,
    ) {
        val baseDir = project.basePath ?: return
        val filePath = Path.of(baseDir, ".idea", "httpRequests", ArmeriaHttpClientEnvironment.FILE_NAME)
        ApplicationManager.getApplication().invokeLater {
            if (project.isDisposed) {
                return@invokeLater
            }
            WriteCommandAction.runWriteCommandAction(
                project,
                message("armeria.run.httpClient.env.write"),
                null,
                {
                    try {
                        val parentDir = filePath.parent.toFile()
                        when {
                            parentDir.exists() && !parentDir.isDirectory -> {
                                LOG.warn("HTTP Client env parent path exists but is not a directory: $parentDir")
                                return@runWriteCommandAction
                            }
                            !parentDir.exists() && !parentDir.mkdirs() -> {
                                LOG.warn("Failed to create HTTP Client env directory: $parentDir")
                                return@runWriteCommandAction
                            }
                        }
                        val parent = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(parentDir)
                        if (parent == null || !parent.isDirectory) {
                            LOG.warn("Failed to resolve HTTP Client env directory in VFS: $parentDir")
                            return@runWriteCommandAction
                        }
                        val existing = parent.findChild(ArmeriaHttpClientEnvironment.FILE_NAME)
                        if (existing != null && existing.isDirectory) {
                            LOG.warn("Cannot write HTTP Client env because path exists as a directory: ${existing.path}")
                            return@runWriteCommandAction
                        }
                        val previous =
                            existing
                                ?.takeIf { it.isValid }
                                ?.let { LoadTextUtil.loadText(it).toString() }
                        val content =
                            ArmeriaHttpClientEnvironment.merge(
                                previous,
                                ArmeriaRunUrlBuilder.LOOPBACK_HOST,
                                listen.port,
                                listen.https,
                            )
                        val virtualFile = existing ?: parent.createChildData(this, ArmeriaHttpClientEnvironment.FILE_NAME)
                        virtualFile.setBinaryContent(content.toByteArray(StandardCharsets.UTF_8))
                    } catch (e: ProcessCanceledException) {
                        throw e
                    } catch (e: Exception) {
                        LOG.warn("Failed to write HTTP Client environment file", e)
                    }
                },
            )
        }
    }
}
