package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiParameter
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaParametersCompilerInspection : AbstractBaseJavaLocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.parameters.flag.display.name")

    override fun getStaticDescription(): String = message("inspection.parameters.flag.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitParameter(parameter: PsiParameter) {
                val annotation = parameter.getAnnotation(ArmeriaRouteSupport.PARAM_ANNOTATION) ?: return
                if (!ArmeriaParametersCompilerSupport.isArmeriaParamWithoutExplicitName(annotation)) {
                    return
                }
                if (ArmeriaParametersCompilerSupport.hasParameterNameOption(parameter, ArmeriaParameterNameMode.JAVA)) {
                    return
                }
                holder.registerProblem(
                    annotation,
                    message("inspection.parameters.flag.problem"),
                )
            }
        }
}
