package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtVisitorVoid

class ArmeriaServerDecoratorKotlinInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.server.decorator.kotlin.display.name")

    override fun getStaticDescription(): String = message("inspection.server.decorator.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitCallExpression(expression: KtCallExpression) {
                super.visitCallExpression(expression)
                for (finding in ArmeriaServerDecoratorKotlinSupport.findings(expression)) {
                    holder.registerProblem(
                        finding.highlight,
                        message(finding.messageKey),
                        ProblemHighlightType.WEAK_WARNING,
                    )
                }
            }
        }
}
