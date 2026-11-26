package com.linecorp.intellij.plugins.armeria.explorer.collector

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.linecorp.intellij.plugins.armeria.explorer.collector.registration.kotlin.ArmeriaKotlinExtendedRegistrationCollector
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.psi.KtFile

/**
 * Kotlin-plugin-dependent collection entry points. Callers must gate access with
 * [com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaKotlinPluginSupport] so these
 * Kotlin PSI types are not resolved when the optional Kotlin plugin is absent.
 */
internal object ArmeriaKotlinRuntimeSupport {
    fun collectExtendedRegistrations(
        project: Project,
        candidateFiles: List<VirtualFile>,
        routes: MutableList<ArmeriaRoute>,
        seenRegistrations: MutableSet<String>,
        alreadyScannedFiles: Set<VirtualFile>,
    ) {
        for (virtualFile in candidateFiles) {
            if (virtualFile.fileType != KotlinFileType.INSTANCE || virtualFile in alreadyScannedFiles) {
                continue
            }
            val ktFile = PsiManager.getInstance(project).findFile(virtualFile) as? KtFile ?: continue
            ArmeriaKotlinExtendedRegistrationCollector.collectFromFile(ktFile, routes, seenRegistrations)
        }
    }

    fun referencesArmeriaKotlinContent(file: PsiFile): Boolean =
        (file as? KtFile)?.let(ArmeriaKotlinRouteCollector::referencesArmeriaKotlinContent) == true
}
