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
            collectBeanServerRegistrations(file, routes, seenServiceRegistrations)
        }
    }

    private fun referencesSpringBootArmeria(file: KtFile): Boolean {
        val hasSpringImports = file.importList?.imports?.any { import ->
            import.importedFqName?.asString()?.startsWith(ArmeriaRouteSupport.ARMERIA_SPRING_PACKAGE_PREFIX) == true
        } ?: false
        if (hasSpringImports) {
            return true
        }
        val searchWindow = file.text.take(ArmeriaRouteSupport.ARMERIA_HEADER_SCAN_LIMIT)
        return ArmeriaRouteSupport.SPRING_BOOT_ARMERIA_FILE_INDICATORS.any(searchWindow::contains)
    }

    private fun collectBeanServerRegistrations(
        file: KtFile,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        for (function in file.collectDescendantsOfType<KtNamedFunction>()) {
            if (!function.hasSpringBeanAnnotation()) {
                continue
            }
            val returnType = ArmeriaKotlinRouteCollector.resolveKotlinReturnTypeText(function.typeReference).orEmpty()
            if (!ArmeriaRouteSupport.isArmeriaServerBeanReturnType(returnType)) {
                continue
            }
            ArmeriaKotlinRouteCollector.collectServiceRegistrationsInScope(
                function,
                routes,
                seenServiceRegistrations,
            )
        }
    }

    private fun KtNamedFunction.hasSpringBeanAnnotation(): Boolean =
        toLightMethods().any { it.hasAnnotation(ArmeriaRouteSupport.SPRING_BEAN_ANNOTATION) }
}
