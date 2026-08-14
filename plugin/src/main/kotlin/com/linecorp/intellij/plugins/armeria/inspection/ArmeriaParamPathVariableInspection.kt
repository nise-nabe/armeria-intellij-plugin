package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaParamPathVariableInspection : AbstractBaseJavaLocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.param.path.variable.display.name")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                val findings =
                    ArmeriaParamPathVariableMismatch.findings(
                        pathVariables = ArmeriaParamPathVariableMismatch.pathVariables(method),
                        bindings = ArmeriaParamPathVariableMismatch.paramBindings(method),
                        missingHighlight = method.nameIdentifier ?: method,
                    )
                for (finding in findings) {
                    holder.registerProblem(finding.highlight, message(finding.messageKey, finding.messageArg))
                }
            }
        }
}
