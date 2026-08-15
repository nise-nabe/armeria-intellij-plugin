package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtVisitorVoid

class ArmeriaParametersCompilerKotlinInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.parameters.flag.kotlin.display.name")

    override fun getStaticDescription(): String = message("inspection.parameters.flag.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitParameter(parameter: KtParameter) {
                super.visitParameter(parameter)
                val entry =
                    parameter.annotationEntries.firstOrNull {
                        ArmeriaKotlinAnnotationSupport.qualifiedName(it) == ArmeriaRouteSupport.PARAM_ANNOTATION
                    } ?: return
                val explicit =
                    ArmeriaKotlinAnnotationSupport
                        .extractStrings(entry)
                        .firstOrNull { it.isNotBlank() }
                if (explicit != null) {
                    return
                }
                if (ArmeriaParametersCompilerSupport.hasParameterNameOption(
                        parameter,
                        ArmeriaParameterNameMode.KOTLIN,
                    )
                ) {
                    return
                }
                holder.registerProblem(
                    entry,
                    message("inspection.parameters.flag.kotlin.problem"),
                )
            }
        }
}
