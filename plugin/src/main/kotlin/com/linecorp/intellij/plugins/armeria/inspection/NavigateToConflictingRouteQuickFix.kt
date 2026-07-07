package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteNavigation
import com.linecorp.intellij.plugins.armeria.explorer.ConflictingRouteRegistration
import com.linecorp.intellij.plugins.armeria.message

internal class NavigateToConflictingRouteQuickFix(
    private val navigationLabel: String,
    private val targetPointer: SmartPsiElementPointer<PsiElement>,
) : LocalQuickFix {
    override fun getFamilyName(): String = message("inspection.duplicate.registration.quickfix.family")

    override fun getName(): String = message("inspection.duplicate.registration.quickfix.navigate", navigationLabel)

    override fun startInWriteAction(): Boolean = false

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        ArmeriaRouteNavigation.navigateToPointer(project, targetPointer)
    }

    companion object {
        fun forConflicts(conflicts: List<ConflictingRouteRegistration>): Array<LocalQuickFix> =
            conflicts
                .map { conflict ->
                    NavigateToConflictingRouteQuickFix(conflict.navigationLabel, conflict.pointer)
                }
                .toTypedArray()
    }
}
