package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.io.IOException

internal object ArmeriaKotlinSpringBootRouteCollector {
    fun collect(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        fallbackScannedFiles: MutableSet<VirtualFile>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        for (virtualFile in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, scope)) {
            if (virtualFile in fallbackScannedFiles) {
                continue
            }
            ArmeriaRouteCollectionMetrics.current()?.filesScanned?.incrementAndGet()
            val contents = loadVirtualFileHeaderText(virtualFile) ?: continue
            if (!ArmeriaRouteSupport.mayReferenceSpringBootArmeriaInText(contents)) {
                continue
            }
            val file = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
            if (!referencesSpringBootArmeria(file)) {
                continue
            }
            fallbackScannedFiles += virtualFile
            ArmeriaRouteCollectionMetrics.current()?.armeriaFiles?.incrementAndGet()
            collectBeanServerRegistrations(file, scope, routes, seenServiceRegistrations)
        }
    }

    private fun referencesSpringBootArmeria(file: KtFile): Boolean {
        if (ArmeriaKotlinRouteCollector.referencesArmeriaKotlinContent(file)) {
            return true
        }
        return hasSpringBootArmeriaFileIndicators(file.viewProvider.contents)
    }

    private fun hasSpringBootArmeriaFileIndicators(contents: CharSequence): Boolean {
        val header = contents.subSequence(0, minOf(contents.length, ArmeriaRouteSupport.ARMERIA_HEADER_SCAN_LIMIT))
        return ArmeriaRouteSupport.SPRING_BOOT_ARMERIA_FILE_INDICATORS.any { indicator ->
            header.contains(indicator)
        }
    }

    private fun loadVirtualFileHeaderText(virtualFile: VirtualFile): CharSequence? =
        try {
            val maxBytes = ArmeriaRouteSupport.ARMERIA_HEADER_SCAN_LIMIT * 4
            val bytesToRead = minOf(virtualFile.length, maxBytes.toLong()).toInt()
            if (bytesToRead <= 0) {
                return null
            }
            val bytes = ByteArray(bytesToRead)
            virtualFile.inputStream.use { input ->
                val read = input.read(bytes)
                if (read <= 0) {
                    return null
                }
                String(bytes, 0, read, virtualFile.charset)
            }
        } catch (_: IOException) {
            null
        }

    private fun collectBeanServerRegistrations(
        file: KtFile,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        for (function in file.collectDescendantsOfType<KtNamedFunction>()) {
            if (!mayHaveSpringBeanAnnotation(function)) {
                continue
            }
            val lightMethods = function.toLightMethods()
            if (!lightMethods.any { it.hasAnnotation(ArmeriaRouteSupport.SPRING_BEAN_ANNOTATION) }) {
                continue
            }
            if (!lightMethods.any { ArmeriaRouteSupport.isArmeriaServerBeanReturnType(it, scope) }) {
                continue
            }
            ArmeriaKotlinRouteCollector.collectServiceRegistrationsInScope(
                function,
                routes,
                seenServiceRegistrations,
            )
        }
    }

    private fun mayHaveSpringBeanAnnotation(function: KtNamedFunction): Boolean {
        if (function.annotationEntries.isEmpty()) {
            return false
        }
        return function.annotationEntries.any { entry ->
            entry.shortName?.asString() == "Bean" ||
                entry.text.contains(ArmeriaRouteSupport.SPRING_BEAN_ANNOTATION)
        }
    }
}
