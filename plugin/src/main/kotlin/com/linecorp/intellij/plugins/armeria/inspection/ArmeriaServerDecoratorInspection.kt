package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaServerDecoratorInspection : AbstractBaseJavaLocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.server.decorator.display.name")

    override fun getStaticDescription(): String = message("inspection.server.decorator.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                for (finding in ArmeriaServerDecoratorSupport.findings(expression)) {
                    holder.registerProblem(
                        finding.highlight,
                        message(finding.messageKey),
                        ProblemHighlightType.WEAK_WARNING,
                    )
                }
            }
        }
}
