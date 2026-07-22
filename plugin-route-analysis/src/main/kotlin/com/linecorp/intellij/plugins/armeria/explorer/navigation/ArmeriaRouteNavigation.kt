package com.linecorp.intellij.plugins.armeria.explorer.navigation
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.concurrency.AppExecutorUtil
import com.linecorp.intellij.plugins.armeria.explorer.model.ArmeriaRoute

object ArmeriaRouteNavigation {
    fun navigateToRoute(
        project: Project,
        route: ArmeriaRoute,
        parentDisposable: Disposable? = null,
    ) {
        navigateToPointer(project, route.pointer, route.sourceOffset, parentDisposable)
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
