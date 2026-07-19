package com.linecorp.intellij.plugins.armeria.explorer.spring

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteCollectContext
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType
import java.io.IOException

object ArmeriaKotlinSpringBootRouteCollector {
    fun collect(context: RouteCollectContext) {
        for (virtualFile in FileTypeIndex.getFiles(KotlinFileType.INSTANCE, context.scope)) {
            if (virtualFile in context.fallbackScannedFiles) {
                continue
            }
            ArmeriaRouteCollectionMetrics.current()?.filesScanned?.incrementAndGet()
            val contents = loadVirtualFileHeaderText(virtualFile) ?: continue
            if (!ArmeriaRouteSupport.mayReferenceSpringBootArmeriaInText(contents)) {
                continue
            }
            val file = PsiManager.getInstance(context.project).findFile(virtualFile) as? KtFile ?: continue
            if (!referencesSpringBootArmeria(file, context)) {
                continue
            }
            context.fallbackScannedFiles += virtualFile
            ArmeriaRouteCollectionMetrics.current()?.armeriaFiles?.incrementAndGet()
            collectBeanServerRegistrations(file, context)
        }
    }

    private fun referencesSpringBootArmeria(
        file: KtFile,
        context: RouteCollectContext,
    ): Boolean {
        if (context.registration.referencesArmeriaKotlinContent(file)) {
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
        context: RouteCollectContext,
    ) {
        for (function in file.collectDescendantsOfType<KtNamedFunction>()) {
            if (!mayHaveSpringBeanAnnotation(function)) {
                continue
            }
            val lightMethods = function.toLightMethods()
            if (!lightMethods.any { it.hasAnnotation(ArmeriaRouteSupport.SPRING_BEAN_ANNOTATION) }) {
                continue
            }
            if (!lightMethods.any { ArmeriaRouteSupport.isArmeriaServerBeanReturnType(it, context.scope) }) {
                continue
            }
            context.registration.collectServiceRegistrationsInScope(
                function,
                context.routes,
                context.seenServiceRegistrations,
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
