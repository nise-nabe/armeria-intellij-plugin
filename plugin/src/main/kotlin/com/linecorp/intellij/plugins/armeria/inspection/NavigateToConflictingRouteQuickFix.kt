package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.message

class NavigateToConflictingRouteQuickFix(
    private val registrationLabel: String,
    private val targetPointer: SmartPsiElementPointer<PsiElement>,
) : LocalQuickFix {
    override fun getFamilyName(): String = message("inspection.duplicate.registration.quickfix.family")

    override fun getName(): String = message("inspection.duplicate.registration.quickfix.navigate", registrationLabel)

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val navigatable = targetPointer.element?.navigationElement as? Navigatable ?: return
        ApplicationManager.getApplication().invokeLater {
            if (navigatable.canNavigate()) {
                navigatable.navigate(true)
            }
        }
    }
}
