package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.linecorp.intellij.plugins.armeria.explorer.ArmeriaRouteDuplicateIndex
import com.linecorp.intellij.plugins.armeria.message

open class ArmeriaDuplicateRegistrationInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.duplicate.registration.display.name")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor {
        val hits = ArmeriaRouteDuplicateIndex.duplicateHitsInFile(holder.project, holder.file)
        if (hits.isEmpty()) {
            return PsiElementVisitor.EMPTY_VISITOR
        }
        return object : PsiElementVisitor() {
            override fun visitFile(visitedFile: PsiFile) {
                for (hit in hits) {
                    val element = hit.pointer.element ?: continue
                    holder.registerProblem(
                        highlightElement(element),
                        message(
                            "inspection.duplicate.registration.problem",
                            hit.registrationLabel,
                            hit.registrationCount,
                        ),
                        *NavigateToConflictingRouteQuickFix.forConflicts(hit.conflictingRoutes),
                    )
                }
            }
        }
    }

    protected open fun highlightElement(element: PsiElement): PsiElement {
        val source = element.navigationElement
        if (source is PsiMethod) {
            return source.nameIdentifier ?: source
        }
        return source
    }
}
