package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtVisitorVoid
import org.jetbrains.kotlin.psi.psiUtil.getParentOfType

class ArmeriaAnnotationProcessorKotlinInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.annotation.processor.kotlin.display.name")

    override fun getStaticDescription(): String = message("inspection.annotation.processor.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : KtVisitorVoid() {
            override fun visitNamedFunction(function: KtNamedFunction) {
                super.visitNamedFunction(function)
                val docComment = function.docComment?.text ?: return
                if (!ArmeriaAnnotationProcessorSupport.hasProcessorConsumedTags(docComment)) {
                    return
                }
                if (ArmeriaKotlinMethodRoute.from(function) == null) {
                    return
                }
                if (hasDescription(function) ||
                    function.getParentOfType<KtClassOrObject>(true)?.let(::hasDescription) == true
                ) {
                    return
                }
                if (ArmeriaAnnotationProcessorSupport.hasDocumentationProcessor(function)) {
                    return
                }
                holder.registerProblem(
                    function.nameIdentifier ?: function,
                    message("inspection.annotation.processor.javadoc.problem"),
                    ProblemHighlightType.WEAK_WARNING,
                )
            }
        }

    private fun hasDescription(function: KtNamedFunction): Boolean =
        function.annotationEntries.any {
            ArmeriaKotlinAnnotationSupport.qualifiedName(it) == ArmeriaRouteSupport.DESCRIPTION_ANNOTATION
        }

    private fun hasDescription(owner: KtClassOrObject): Boolean =
        owner.annotationEntries.any {
            ArmeriaKotlinAnnotationSupport.qualifiedName(it) == ArmeriaRouteSupport.DESCRIPTION_ANNOTATION
        }
}
