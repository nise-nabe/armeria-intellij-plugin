package com.linecorp.intellij.plugins.armeria.explorer.collector

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteCollectionMetrics
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaScalaTextSupport

internal object ArmeriaScalaRouteCollector {
    fun collectServiceRegistrationsFallback(
        project: Project,
        scope: GlobalSearchScope,
        routes: MutableList<ArmeriaRoute>,
        fallbackScannedFiles: MutableSet<VirtualFile>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        for (virtualFile in FilenameIndex.getAllFilesByExt(project, "scala", scope)) {
            if (virtualFile in fallbackScannedFiles) {
                continue
            }
            ArmeriaRouteCollectionMetrics.current()?.filesScanned?.incrementAndGet()
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile) ?: continue
            val contents = psiFile.text
            if (!ArmeriaScalaTextSupport.referencesArmeriaScalaContent(contents)) {
                continue
            }
            fallbackScannedFiles += virtualFile
            ArmeriaRouteCollectionMetrics.current()?.armeriaFiles?.incrementAndGet()
            collectServiceRegistrationsFromFile(psiFile, contents, routes, seenServiceRegistrations)
        }
    }

    private fun collectServiceRegistrationsFromFile(
        file: PsiFile,
        contents: String,
        routes: MutableList<ArmeriaRoute>,
        seenServiceRegistrations: MutableSet<String>,
    ) {
        for (match in ArmeriaScalaTextSupport.findServiceRegistrations(contents)) {
            val element = file.findElementAt(match.startOffset) ?: file
            val target = ArmeriaScalaTextSupport.renderScalaTarget(match.targetText)
            val targetUnresolved = ArmeriaScalaTextSupport.isUnresolvedScalaTarget(match.targetText, target)
            ArmeriaRouteCollectorServiceRegistration.addServiceRegistrationRoute(
                element = element,
                registrationKey = "${file.virtualFile?.path}:${match.startOffset}",
                methodName = match.methodName,
                path = match.path,
                target = target,
                targetUnresolved = targetUnresolved,
                implementationText = match.targetText,
                argumentCount = match.argumentCount,
                routes = routes,
                seenServiceRegistrations = seenServiceRegistrations,
            )
        }
    }
}
