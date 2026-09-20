package com.linecorp.intellij.plugins.armeria.explorer.navigation

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.linecorp.intellij.plugins.armeria.explorer.model.FileServiceRoot
import com.linecorp.intellij.plugins.armeria.explorer.model.FileServiceRootKind
import org.jetbrains.jps.model.java.JavaResourceRootType
import java.io.File

/**
 * Resolves a [FileServiceRoot] declared on a `FileService` registration to a [VirtualFile]
 * inside the project: a file-system directory (relative paths anchored at the project or a
 * content root) or a class-path resource directory (resource roots, then content roots).
 */
object ArmeriaFileServiceRootResolver {
    private val INDEX_FILE_NAMES = listOf("index.html", "index.htm")

    fun resolve(
        project: Project,
        root: FileServiceRoot,
    ): VirtualFile? =
        when (root.kind) {
            FileServiceRootKind.FILE_SYSTEM -> resolveFileSystemRoot(project, root.path)
            FileServiceRootKind.CLASS_PATH -> resolveClassPathRoot(project, root)
        }

    /** A sample index file served by the directory, if present. */
    fun indexFileFor(directory: VirtualFile): VirtualFile? = INDEX_FILE_NAMES.firstNotNullOfOrNull(directory::findChild)

    private fun resolveFileSystemRoot(
        project: Project,
        rawPath: String,
    ): VirtualFile? {
        val path = FileUtil.toSystemIndependentName(rawPath.trim())
        if (path.isEmpty()) {
            return null
        }
        if (File(path).isAbsolute || path.startsWith("/")) {
            return LocalFileSystem.getInstance().refreshAndFindFileByPath(path)
        }
        val relative = path.trimStart('/')
        for (base in projectBaseCandidates(project)) {
            base.findFileByRelativePath(relative)?.let { return it }
        }
        return null
    }

    private fun resolveClassPathRoot(
        project: Project,
        root: FileServiceRoot,
    ): VirtualFile? {
        val declared = root.path.trim().trimStart('/')
        if (declared.isEmpty()) {
            return null
        }
        val anchored = !root.path.trimStart().startsWith("/") && root.anchorClassName.isNotEmpty()
        val relative =
            if (anchored) {
                val packagePath = root.anchorClassName.substringBeforeLast('.', "").replace('.', '/')
                if (packagePath.isEmpty()) declared else "$packagePath/$declared"
            } else {
                declared
            }
        for (base in classPathRootCandidates(project)) {
            base.findFileByRelativePath(relative)?.let { return it }
        }
        return null
    }

    private fun projectBaseCandidates(project: Project): List<VirtualFile> {
        val candidates = LinkedHashSet<VirtualFile>()
        project.guessProjectDir()?.let(candidates::add)
        ProjectRootManager
            .getInstance(project)
            .contentRoots
            .sortedBy { it.path }
            .forEach(candidates::add)
        return candidates.toList()
    }

    private fun classPathRootCandidates(project: Project): List<VirtualFile> {
        val candidates = LinkedHashSet<VirtualFile>()
        for (module in ModuleManager.getInstance(project).modules) {
            val rootManager = ModuleRootManager.getInstance(module)
            candidates +=
                (
                    rootManager.getSourceRoots(JavaResourceRootType.RESOURCE) +
                        rootManager.getSourceRoots(JavaResourceRootType.TEST_RESOURCE)
                ).sortedBy { it.path }
        }
        candidates += ProjectRootManager.getInstance(project).contentRoots.sortedBy { it.path }
        return candidates.toList()
    }
}
