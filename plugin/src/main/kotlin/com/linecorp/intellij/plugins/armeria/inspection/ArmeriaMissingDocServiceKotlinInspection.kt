package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

class ArmeriaMissingDocServiceKotlinInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.missing.docservice.kotlin.display.name")

    override fun getStaticDescription(): String = message("inspection.missing.docservice.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                register(holder, ArmeriaMissingDocServiceKotlinSupport.highlight(function))
            }

            override fun visitLambdaExpression(expression: KtLambdaExpression) {
                if (PsiTreeUtil.getParentOfType(expression, KtNamedFunction::class.java) != null) {
                    return
                }
                register(holder, ArmeriaMissingDocServiceKotlinSupport.highlight(expression))
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
