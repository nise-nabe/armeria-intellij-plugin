package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiLambdaExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaMissingDocServiceInspection : AbstractBaseJavaLocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.missing.docservice.display.name")

    override fun getStaticDescription(): String = message("inspection.missing.docservice.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                register(holder, ArmeriaMissingDocServiceSupport.highlight(method))
            }

            override fun visitLambdaExpression(expression: PsiLambdaExpression) {
                if (PsiTreeUtil.getParentOfType(expression, PsiMethod::class.java) != null) {
                    return
                }
                register(holder, ArmeriaMissingDocServiceSupport.highlight(expression))
            }
        }

    private fun register(
        holder: ProblemsHolder,
        highlight: PsiElement?,
    ) {
        if (highlight == null) {
            return
        }
        holder.registerProblem(
            highlight,
            message("inspection.missing.docservice.problem"),
            ProblemHighlightType.INFORMATION,
        )
    }
}
