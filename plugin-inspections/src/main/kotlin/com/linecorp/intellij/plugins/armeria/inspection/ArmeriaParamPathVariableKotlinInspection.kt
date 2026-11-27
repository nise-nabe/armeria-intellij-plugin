package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid

class ArmeriaParamPathVariableKotlinInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.param.path.variable.kotlin.display.name")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                val findings =
                    ArmeriaParamPathVariableMismatch.findings(
                        pathVariables = ArmeriaKotlinAnnotationSupport.pathVariables(function),
                        bindings = ArmeriaKotlinAnnotationSupport.paramBindings(function),
                        missingHighlight = function.nameIdentifier ?: function,
                    )
                for (finding in findings) {
                    holder.registerProblem(finding.highlight, message(finding.messageKey, finding.messageArg))
                }
            }
        }
}
