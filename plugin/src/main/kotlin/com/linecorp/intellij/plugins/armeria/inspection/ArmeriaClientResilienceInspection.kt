package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaClientResilienceInspection : AbstractBaseJavaLocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.production.client.resilience.display.name")

    override fun getStaticDescription(): String = message("inspection.production.client.resilience.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                val highlight = ArmeriaClientResilienceSupport.highlight(expression) ?: return
                holder.registerProblem(
                    highlight,
                    message("inspection.production.client.resilience.problem"),
                    ProblemHighlightType.INFORMATION,
                )
            }
        }
}
