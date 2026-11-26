package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.indexing.DataIndexer
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexExtension
import com.intellij.util.indexing.FileContent
import com.intellij.util.indexing.ID
import com.intellij.util.indexing.ScalarIndexExtension
import com.intellij.util.io.EnumeratorIntegerDescriptor
import java.io.IOException

/**
 * Incrementally tracks source files whose text can contain an Armeria reference.
 * Route collectors still parse PSI for these candidates, but unchanged unrelated sources
 * no longer need to be read on every project-level cache recomputation.
 */
class ArmeriaReferenceFileIndex : ScalarIndexExtension<Int>() {
    override fun getName(): ID<Int, Void> = NAME

    override fun getIndexer(): DataIndexer<Int, Void, FileContent> =
        DataIndexer { input ->
            val contents = input.contentAsText
            if (contents.contains(ArmeriaRouteSupport.ARMERIA_PACKAGE_PREFIX)) {
                mapOf(ARMERIA_REFERENCE_KEY to null)
            } else {
                emptyMap()
            }
        }

    override fun getKeyDescriptor(): EnumeratorIntegerDescriptor = EnumeratorIntegerDescriptor.INSTANCE

    override fun getInputFilter(): FileBasedIndex.InputFilter = INPUT_FILTER

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 2

    companion object {
        private const val ARMERIA_REFERENCE_KEY = 1
        private val SOURCE_EXTENSIONS = setOf("java", "kt", "kts", "scala")

        val NAME: ID<Int, Void> = ID.create("com.linecorp.intellij.plugins.armeria.reference.file")

        private val INPUT_FILTER =
            FileBasedIndex.InputFilter { file ->
                file.extension?.lowercase() in SOURCE_EXTENSIONS
            }

        fun findFiles(
            project: Project,
            scope: GlobalSearchScope,
        ): List<VirtualFile> {
            if (!isRegistered()) {
                return fallbackFiles(project, scope)
            }
            val files = mutableListOf<VirtualFile>()
            FileBasedIndex.getInstance().getFilesWithKey(
                NAME,
                setOf(ARMERIA_REFERENCE_KEY),
                { file ->
                    files += file
                    true
                },
                scope,
            )
            return files.sortedWith(compareBy({ it.path }, { it.name }))
        }

        private fun isRegistered(): Boolean =
            FileBasedIndexExtension.EXTENSION_POINT_NAME.extensionList.any { extension ->
                extension.name.name == NAME.name
            }

        private fun fallbackFiles(
            project: Project,
            scope: GlobalSearchScope,
        ): List<VirtualFile> =
            SOURCE_EXTENSIONS
                .flatMap { extension -> FilenameIndex.getAllFilesByExt(project, extension, scope) }
                .distinct()
                .filter { file ->
                    loadText(file).contains(ArmeriaRouteSupport.ARMERIA_PACKAGE_PREFIX)
                }.sortedWith(compareBy({ it.path }, { it.name }))

        private fun loadText(file: VirtualFile): String =
            try {
                VfsUtilCore.loadText(file)
            } catch (_: IOException) {
                ""
            }
    }
}
