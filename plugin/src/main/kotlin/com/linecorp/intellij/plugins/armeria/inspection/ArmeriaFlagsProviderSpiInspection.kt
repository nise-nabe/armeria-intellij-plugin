package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementVisitor
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaFlagsProviderSpiInspection : AbstractBaseJavaLocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.production.flags.provider.display.name")

    override fun getStaticDescription(): String = message("inspection.production.flags.provider.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                val highlight = ArmeriaFlagsProviderSpiSupport.highlight(aClass) ?: return
                holder.registerProblem(
                    highlight,
                    message("inspection.production.flags.provider.problem"),
                    ProblemHighlightType.INFORMATION,
                )
            }
        }
}
