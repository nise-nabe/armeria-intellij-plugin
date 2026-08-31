package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

class ArmeriaSuspendWithoutKotlinInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.missing.armeria.kotlin.display.name")

    override fun getStaticDescription(): String = message("inspection.missing.armeria.kotlin.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                if (!function.hasModifier(KtTokens.SUSPEND_KEYWORD)) {
                    return
                }
                if (ArmeriaKotlinMethodRoute.from(function) == null) {
                    return
                }
                if (ArmeriaKotlinClasspath.isPresent(function)) {
                    return
                }
                holder.registerProblem(
                    highlight(function),
                    message("inspection.missing.armeria.kotlin.problem"),
                    ProblemHighlightType.WEAK_WARNING,
                )
            }
        }

    private fun highlight(function: KtNamedFunction): PsiElement =
        function.modifierList
            ?.node
            ?.findChildByType(KtTokens.SUSPEND_KEYWORD)
            ?.psi
            ?: function.nameIdentifier
            ?: function
}
