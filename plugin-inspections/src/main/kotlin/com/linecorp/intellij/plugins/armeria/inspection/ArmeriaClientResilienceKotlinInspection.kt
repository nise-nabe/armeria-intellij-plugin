package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

class ArmeriaClientResilienceKotlinInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.production.client.resilience.kotlin.display.name")

    override fun getStaticDescription(): String = message("inspection.production.client.resilience.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                val highlight = ArmeriaClientResilienceKotlinSupport.highlight(expression) ?: return
                holder.registerProblem(
                    highlight,
                    message("inspection.production.client.resilience.problem"),
                    ProblemHighlightType.INFORMATION,
                )
            }
        }
}
