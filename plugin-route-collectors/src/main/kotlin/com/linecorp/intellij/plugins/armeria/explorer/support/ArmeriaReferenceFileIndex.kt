package com.linecorp.intellij.plugins.armeria.explorer.support

import com.intellij.openapi.fileEditor.FileDocumentManager
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
            buildMap {
                if (referencesArmeriaPackage(contents)) {
                    put(ARMERIA_REFERENCE_KEY, null)
                }
                if (ArmeriaRouteSupport.SPRING_BOOT_ARMERIA_FILE_INDICATORS.any(contents::contains)) {
                    put(SPRING_BOOT_ARMERIA_REFERENCE_KEY, null)
                }
                if (containsDocServiceExampleReference(contents)) {
                    put(DOC_SERVICE_EXAMPLE_REFERENCE_KEY, null)
                }
                if (containsJUnitServerExtensionReference(contents)) {
                    put(JUNIT_SERVER_EXTENSION_REFERENCE_KEY, null)
                }
            }
        }

    override fun getKeyDescriptor(): EnumeratorIntegerDescriptor = EnumeratorIntegerDescriptor.INSTANCE

    override fun getInputFilter(): FileBasedIndex.InputFilter = INPUT_FILTER

    override fun dependsOnFileContent(): Boolean = true

    override fun getVersion(): Int = 2

    companion object {
        private const val ARMERIA_REFERENCE_KEY = 1
        private const val SPRING_BOOT_ARMERIA_REFERENCE_KEY = 2
        private const val DOC_SERVICE_EXAMPLE_REFERENCE_KEY = 3
        private const val JUNIT_SERVER_EXTENSION_REFERENCE_KEY = 4
        private val SOURCE_EXTENSIONS = setOf("java", "kt", "kts", "scala")
        private val ARMERIA_PACKAGE_PATTERN = Regex("""com\s*\.\s*linecorp\s*\.\s*armeria""")

        val NAME: ID<Int, Void> = ID.create("com.linecorp.intellij.plugins.armeria.reference.file")

        private val INPUT_FILTER =
            FileBasedIndex.InputFilter { file ->
                file.extension?.lowercase() in SOURCE_EXTENSIONS
            }

        fun findFiles(
            project: Project,
            scope: GlobalSearchScope,
            includeSpringBootArmeriaIndicators: Boolean = false,
        ): List<VirtualFile> {
            val keys =
                if (includeSpringBootArmeriaIndicators) {
                    setOf(ARMERIA_REFERENCE_KEY, SPRING_BOOT_ARMERIA_REFERENCE_KEY)
                } else {
                    setOf(ARMERIA_REFERENCE_KEY)
                }
            if (!isRegistered()) {
                return fallbackFiles(project, scope, includeSpringBootArmeriaIndicators)
            }
            return filesWithKeys(project, scope, keys)
        }

        fun findDocServiceExampleFiles(
            project: Project,
            scope: GlobalSearchScope,
        ): List<VirtualFile> =
            findIndexedFiles(project, scope, DOC_SERVICE_EXAMPLE_REFERENCE_KEY) { contents ->
                containsDocServiceExampleReference(contents)
            }

        fun findJUnitServerExtensionFiles(
            project: Project,
            scope: GlobalSearchScope,
        ): List<VirtualFile> =
            findIndexedFiles(project, scope, JUNIT_SERVER_EXTENSION_REFERENCE_KEY) { contents ->
                containsJUnitServerExtensionReference(contents)
            }

        private fun findIndexedFiles(
            project: Project,
            scope: GlobalSearchScope,
            key: Int,
            fallbackPredicate: (String) -> Boolean,
        ): List<VirtualFile> {
            if (!isRegistered()) {
                return fallbackFiles(project, scope, fallbackPredicate)
            }
            return filesWithKeys(project, scope, setOf(key))
        }

        private fun filesWithKeys(
            project: Project,
            scope: GlobalSearchScope,
            keys: Set<Int>,
        ): List<VirtualFile> {
            val files = linkedSetOf<VirtualFile>()
            for (key in keys) {
                FileBasedIndex.getInstance().getFilesWithKey(
                    NAME,
                    setOf(key),
                    { file ->
                        files += file
                        true
                    },
                    scope,
                )
            }
            return files.sortedWith(compareBy({ it.path }, { it.name }))
        }

        internal fun referencesArmeriaPackage(contents: CharSequence): Boolean = ARMERIA_PACKAGE_PATTERN.containsMatchIn(contents)

        internal fun containsDocServiceExampleReference(contents: CharSequence): Boolean =
            contents.contains("exampleRequests") || contents.contains("exampleHeaders")

        internal fun containsJUnitServerExtensionReference(contents: CharSequence): Boolean =
            contents.contains(REGISTER_EXTENSION_REFERENCE)

        private const val REGISTER_EXTENSION_REFERENCE = "RegisterExtension"

        private fun isRegistered(): Boolean =
            FileBasedIndexExtension.EXTENSION_POINT_NAME.extensionList.any { extension ->
                extension.name.name == NAME.name
            }

        private fun fallbackFiles(
            project: Project,
            scope: GlobalSearchScope,
            includeSpringBootArmeriaIndicators: Boolean,
        ): List<VirtualFile> =
            fallbackFiles(project, scope) { contents ->
                referencesArmeriaPackage(contents) ||
                    (
                        includeSpringBootArmeriaIndicators &&
                            ArmeriaRouteSupport.SPRING_BOOT_ARMERIA_FILE_INDICATORS.any(contents::contains)
                    )
            }

        private fun fallbackFiles(
            project: Project,
            scope: GlobalSearchScope,
            predicate: (String) -> Boolean,
        ): List<VirtualFile> =
            SOURCE_EXTENSIONS
                .flatMap { extension -> FilenameIndex.getAllFilesByExt(project, extension, scope) }
                .distinct()
                .filter { file -> predicate(loadText(file)) }
                .sortedWith(compareBy({ it.path }, { it.name }))

        private fun loadText(file: VirtualFile): String = FileDocumentManager.getInstance().getDocument(file)?.text ?: loadSavedText(file)

        private fun loadSavedText(file: VirtualFile): String =
            try {
                VfsUtilCore.loadText(file)
            } catch (_: IOException) {
                ""
            }
    }
}
