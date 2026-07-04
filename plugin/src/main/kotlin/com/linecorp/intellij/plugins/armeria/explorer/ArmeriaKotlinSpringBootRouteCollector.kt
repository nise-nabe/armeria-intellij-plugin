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
        val hasArmeriaImports = file.importList?.imports?.any { import ->
            import.importedFqName?.asString()?.startsWith(ArmeriaRouteSupport.ARMERIA_PACKAGE_PREFIX) == true
        } ?: false
        if (hasArmeriaImports) {
            return true
        }
        val contents = file.viewProvider.contents
        if (ArmeriaRouteSupport.referencesArmeriaInText(contents)) {
            return true
        }
        val searchWindow = contents.subSequence(0, minOf(contents.length, ArmeriaRouteSupport.ARMERIA_HEADER_SCAN_LIMIT))
        return ArmeriaRouteSupport.SPRING_BOOT_ARMERIA_FILE_INDICATORS.any(searchWindow::contains)
    }

    private fun collectBeanServerRegistrations(
        file: KtFile,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        for (function in file.collectDescendantsOfType<KtNamedFunction>()) {
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
}
