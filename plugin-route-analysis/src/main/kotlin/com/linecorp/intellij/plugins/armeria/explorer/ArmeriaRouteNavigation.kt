package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.util.concurrency.AppExecutorUtil

object ArmeriaRouteNavigation {
    fun navigateToPointer(
        project: Project,
        pointer: SmartPsiElementPointer<PsiElement>,
        parentDisposable: Disposable? = null,
    ) {
        val coordinator = ReadAction.nonBlocking<Navigatable?> {
            val element = pointer.element
            (element as? Navigatable)?.takeIf { it.canNavigate() }
                ?: (element?.navigationElement as? Navigatable)?.takeIf { it.canNavigate() }
        }
            .inSmartMode(project)
        if (parentDisposable != null) {
            coordinator.expireWith(parentDisposable)
        }
        coordinator
            .finishOnUiThread(ModalityState.any()) { navigatable ->
                navigatable?.navigate(true)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }
}
