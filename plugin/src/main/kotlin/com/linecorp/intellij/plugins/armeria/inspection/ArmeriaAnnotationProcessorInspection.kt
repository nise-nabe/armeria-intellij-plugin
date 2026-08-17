package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.JavaElementVisitor
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message

class ArmeriaAnnotationProcessorInspection : AbstractBaseJavaLocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.annotation.processor.display.name")

    override fun getStaticDescription(): String = message("inspection.annotation.processor.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : JavaElementVisitor() {
            override fun visitMethod(method: PsiMethod) {
                val docComment = method.docComment?.text ?: return
                if (!ArmeriaAnnotationProcessorSupport.hasProcessorConsumedTags(docComment)) {
                    return
                }
                if (ArmeriaRouteSupport.findRouteAnnotation(method) == null) {
                    return
                }
                if (method.getAnnotation(ArmeriaRouteSupport.DESCRIPTION_ANNOTATION) != null ||
                    method.containingClass?.getAnnotation(ArmeriaRouteSupport.DESCRIPTION_ANNOTATION) != null
                ) {
                    return
                }
                if (ArmeriaAnnotationProcessorSupport.hasDocumentationProcessor(method)) {
                    return
                }
                holder.registerProblem(
                    method.nameIdentifier ?: method,
                    message("inspection.annotation.processor.javadoc.problem"),
                    ProblemHighlightType.WEAK_WARNING,
                )
            }
        }
}
