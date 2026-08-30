package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaServerLimitsInspection : AbstractBaseJavaLocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.production.server.limits.display.name")

    override fun getStaticDescription(): String = message("inspection.production.server.limits.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                val missing = ArmeriaServerLimitsSupport.missingLimits(expression)
                if (missing.isEmpty()) {
                    return
                }
                val highlight = ArmeriaServerLimitsSupport.highlight(expression) ?: return
                holder.registerProblem(
                    highlight,
                    message(
                        "inspection.production.server.limits.problem",
                        ArmeriaProductionChecklist.formatMissingLimits(missing),
                    ),
                    ProblemHighlightType.INFORMATION,
                )
            }
        }
}
