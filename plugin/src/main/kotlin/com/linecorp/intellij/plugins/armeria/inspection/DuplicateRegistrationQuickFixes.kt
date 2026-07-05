package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteDuplicateIndex

internal object DuplicateRegistrationQuickFixes {
    fun forElement(project: Project, element: PsiElement): Array<LocalQuickFix> {
        return ArmeriaRouteDuplicateIndex.conflictingRoutes(project, element)
            .map { route ->
                NavigateToConflictingRouteQuickFix(
                    ArmeriaRouteDuplicateIndex.duplicateRegistrationLabel(route),
                    route.pointer,
                )
            }
            .toTypedArray()
    }
}
