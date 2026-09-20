package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.message
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.expr.MethodInvocation
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScExpression
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScFunctionExpr
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScReferenceExpression
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition

class ArmeriaMissingBlockingScalaInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.missing.blocking.scala.display.name")

    override fun getStaticDescription(): String = message("inspection.missing.blocking.description")

    override fun buildVisitor(
        holder: ProblemsHolder,
        isOnTheFly: Boolean,
    ): PsiElementVisitor =
        object : ScalaElementVisitor() {
            override fun visitFunctionDefinition(function: ScFunctionDefinition) {
                super.visitFunctionDefinition(function)
                if (!shouldInspect(function)) {
                    return
                }
                val body = function.body().orNull() ?: return
                val messageKey = problemMessageKey(function)
                for (finding in findingsIn(body, function)) {
                    holder.registerProblem(
                        finding.highlight,
                        message(messageKey, finding.methodName),
                        ProblemHighlightType.WEAK_WARNING,
                    )
                }
            }
        }

    private fun shouldInspect(function: ScFunction): Boolean {
        if (function.isConstructor) {
            return false
        }
        if (hasAnnotation(function, ArmeriaRouteSupport.NON_BLOCKING_ANNOTATION) ||
            hasAnnotation(function.containingClass, ArmeriaRouteSupport.NON_BLOCKING_ANNOTATION)
        ) {
            return false
        }
        val honorsBlocking =
            ArmeriaScalaInspectionSupport.routeAnnotation(function) != null ||
                ArmeriaMissingBlockingSupport.isGrpcServiceOverride(function)
        if (honorsBlocking) {
            return !hasAnnotation(function, ArmeriaRouteSupport.BLOCKING_ANNOTATION) &&
                !hasAnnotation(function.containingClass, ArmeriaRouteSupport.BLOCKING_ANNOTATION)
        }
        return ArmeriaMissingBlockingSupport.isHttpServiceOverride(function) ||
            ArmeriaMissingBlockingSupport.isEventLoopDataFetcher(function)
    }

    private fun problemMessageKey(function: ScFunction): String =
        when {
            ArmeriaScalaInspectionSupport.routeAnnotation(function) != null ||
                ArmeriaMissingBlockingSupport.isGrpcServiceOverride(function) ->
                "inspection.missing.blocking.problem"
            ArmeriaMissingBlockingSupport.isDataFetcherGet(function) ->
                "inspection.missing.blocking.problem.graphql"
            else -> "inspection.missing.blocking.problem.httpservice"
        }

    private fun hasAnnotation(
        owner: PsiModifierListOwner?,
        qualifiedName: String,
    ): Boolean = owner?.annotations?.any { it.qualifiedName == qualifiedName } == true

    private fun findingsIn(
        scope: ScExpression,
        boundary: PsiElement,
    ): List<ArmeriaBlockingCallFinding> {
        val findings = mutableListOf<ArmeriaBlockingCallFinding>()
        scope.forEachDescendant { element ->
            val reference = invocationReference(element) ?: return@forEachDescendant
            if (!isOnInspectedPath(boundary, element)) {
                return@forEachDescendant
            }
            val methodName = reference.refName() ?: return@forEachDescendant
            val resolved = reference.resolve() as? PsiMethod
            if (!ArmeriaBlockingCallPatterns.isBlockingCall(
                    methodName = methodName,
                    ownerFqn = resolved?.containingClass?.qualifiedName,
                    unresolved = resolved == null,
                    qualifierText = reference.qualifier().orNull()?.text,
                    argumentCount = (element as? MethodInvocation)?.argumentExpressions()?.size() ?: 0,
                )
            ) {
                return@forEachDescendant
            }
            findings += ArmeriaBlockingCallFinding(highlight = reference, methodName = methodName)
        }
        return findings
    }

    private fun invocationReference(element: PsiElement): ScReferenceExpression? =
        when (element) {
            is MethodInvocation -> element.invokedExpr as? ScReferenceExpression
            is ScReferenceExpression ->
                element.takeIf {
                    it.parent !is MethodInvocation &&
                        it.parent !is ScReferenceExpression &&
                        it.resolve() is PsiMethod
                }
            else -> null
        }

    private fun isOnInspectedPath(
        boundary: PsiElement,
        element: PsiElement,
    ): Boolean {
        var current: PsiElement? = element.parent
        while (current != null && current != boundary) {
            when (current) {
                is ScFunctionExpr, is ScFunction, is PsiClass -> return false
            }
            current = current.parent
        }
        return current == boundary
    }
}
