package com.linecorp.intellij.plugins.armeria.explorer.spring

import com.intellij.psi.PsiManager
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaReferenceFileIndex
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.explorer.support.RouteCollectContext
import com.linecorp.intellij.plugins.armeria.explorer.support.toArmeriaLightMethods
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

object ArmeriaKotlinSpringBootRouteCollector {
    fun collect(context: RouteCollectContext) {
        val candidateFiles =
            ArmeriaReferenceFileIndex.findFiles(
                context.project,
                context.scope,
                includeSpringBootArmeriaIndicators = true,
            )
        for (virtualFile in candidateFiles) {
            if (virtualFile.fileType != KotlinFileType.INSTANCE || virtualFile in context.fallbackScannedFiles) {
                continue
            }
            ArmeriaRouteCollectionMetrics.current()?.filesScanned?.incrementAndGet()
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

    private fun hasSpringBootArmeriaFileIndicators(contents: CharSequence): Boolean =
        ArmeriaRouteSupport.SPRING_BOOT_ARMERIA_FILE_INDICATORS.any { indicator ->
            contents.contains(indicator)
        }

    private fun collectBeanServerRegistrations(
        file: KtFile,
        context: RouteCollectContext,
    ) {
        for (function in file.collectDescendantsOfType<KtNamedFunction>()) {
            if (!mayHaveSpringBeanAnnotation(function)) {
                continue
            }
            val lightMethods = function.toArmeriaLightMethods()
            if (!lightMethods.any { it.hasAnnotation(ArmeriaRouteSupport.SPRING_BEAN_ANNOTATION) }) {
                continue
            }
            if (!lightMethods.any { ArmeriaRouteSupport.isArmeriaServerBeanReturnType(it) }) {
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
