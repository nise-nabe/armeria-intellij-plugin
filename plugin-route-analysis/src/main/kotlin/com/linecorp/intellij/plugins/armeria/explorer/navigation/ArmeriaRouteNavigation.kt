package com.linecorp.intellij.plugins.armeria.explorer.navigation
import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.concurrency.AppExecutorUtil
import com.linecorp.intellij.plugins.armeria.expireWithPluginUnload
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute
import com.linecorp.intellij.plugins.armeria.explorer.model.RouteMatch

object ArmeriaRouteNavigation {
    fun navigateToRoute(
        project: Project,
        route: ArmeriaRoute,
        parentDisposable: Disposable? = null,
    ) {
        val fileServiceRoot = route.fileServiceRoot
        if (route.routeMatch == RouteMatch.FILE_SERVICE && fileServiceRoot != null) {
            navigateToFileServiceRoot(project, route, parentDisposable)
            return
        }
        navigateToPointer(project, route.pointer, route.sourceOffset, parentDisposable)
    }

    private fun navigateToFileServiceRoot(
        project: Project,
        route: ArmeriaRoute,
        parentDisposable: Disposable?,
    ) {
        val root = route.fileServiceRoot ?: return
        ReadAction
            .nonBlocking<VirtualFile?> {
                ArmeriaFileServiceRootResolver.resolve(project, root)
            }.inSmartMode(project)
            .expireWith(project)
            .expireWithPluginUnload()
            .let { coordinator ->
                if (parentDisposable != null) coordinator.expireWith(parentDisposable) else coordinator
            }.finishOnUiThread(ModalityState.any()) { target ->
                if (target == null) {
                    navigateToPointer(project, route.pointer, route.sourceOffset, parentDisposable)
                    return@finishOnUiThread
                }
                revealFileServiceRoot(project, target)
            }.submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun revealFileServiceRoot(
        project: Project,
        target: VirtualFile,
    ) {
        val fileToOpen =
            if (target.isDirectory) {
                ArmeriaFileServiceRootResolver.indexFileFor(target)
            } else {
                target
            }
        val toReveal = fileToOpen ?: target
        ProjectView.getInstance(project).select(toReveal, toReveal, false)
        fileToOpen?.let { OpenFileDescriptor(project, it).navigate(true) }
    }

    fun navigateToPointer(
        project: Project,
        pointer: SmartPsiElementPointer<PsiElement>,
        sourceOffset: Int? = null,
        parentDisposable: Disposable? = null,
    ) {
        ReadAction
            .nonBlocking<Navigatable?> {
                resolveNavigatable(pointer, sourceOffset)
            }.inSmartMode(project)
            .expireWith(project)
            .expireWithPluginUnload()
            .let { coordinator ->
                if (parentDisposable != null) coordinator.expireWith(parentDisposable) else coordinator
            }.finishOnUiThread(ModalityState.any()) { navigatable ->
                navigatable?.navigate(true)
            }.submit(AppExecutorUtil.getAppExecutorService())
    }

    private fun resolveNavigatable(
        pointer: SmartPsiElementPointer<PsiElement>,
        sourceOffset: Int?,
    ): Navigatable? {
        val element = pointer.element ?: return null
        if (sourceOffset != null) {
            val virtualFile = element.containingFile?.virtualFile
            if (virtualFile != null) {
                return OpenFileDescriptor(element.project, virtualFile, sourceOffset)
            }
        }
        return (element as? Navigatable)?.takeIf { it.canNavigate() }
            ?: (element.navigationElement as? Navigatable)?.takeIf { it.canNavigate() }
    }
}
