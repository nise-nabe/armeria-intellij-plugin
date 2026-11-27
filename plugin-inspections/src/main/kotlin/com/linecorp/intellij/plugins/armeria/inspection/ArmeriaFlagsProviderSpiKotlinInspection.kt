package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtVisitorVoid

class ArmeriaFlagsProviderSpiKotlinInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.production.flags.provider.kotlin.display.name")

    override fun getStaticDescription(): String = message("inspection.production.flags.provider.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitClassOrObject(classOrObject: KtClassOrObject) {
                super.visitClassOrObject(classOrObject)
                val highlight = ArmeriaFlagsProviderSpiKotlinSupport.highlight(classOrObject) ?: return
                holder.registerProblem(
                    highlight,
                    message("inspection.production.flags.provider.problem"),
                    ProblemHighlightType.INFORMATION,
                )
            }
        }
}
