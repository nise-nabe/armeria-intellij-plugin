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
import org.jetbrains.plugins.scala.lang.psi.api.expr.ScUnderscoreSection
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunction
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition

class ArmeriaMissingBlockingScalaInspection : LocalInspectionTool() {
    override fun getDisplayName(): String = message("inspection.missing.blocking.scala.display.name")

    override fun getStaticDescription(): String = message("inspection.missing.blocking.scala.description")

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
                isGrpcServiceOverride(function)
        if (honorsBlocking) {
            return !hasAnnotation(function, ArmeriaRouteSupport.BLOCKING_ANNOTATION) &&
                !hasAnnotation(function.containingClass, ArmeriaRouteSupport.BLOCKING_ANNOTATION)
        }
        // GraphQL DataFetcher coverage relies on Java PSI (PsiMethodCallExpression et al.) and
        // never matches Scala trees, so only the HttpService override path applies here.
        return isHttpServiceOverride(function)
    }

    private fun problemMessageKey(function: ScFunction): String =
        when {
            ArmeriaScalaInspectionSupport.routeAnnotation(function) != null ||
                isGrpcServiceOverride(function) ->
                "inspection.missing.blocking.problem"
            else -> "inspection.missing.blocking.problem.httpservice"
        }

    // PsiMethod.findSuperMethods and PsiClass.getSupers are not wired for Scala PSI in a
    // reliable way, so overrides are detected from the syntactic template parents.
    private fun hasSuperMethod(function: ScFunction): Boolean =
        ArmeriaScalaInspectionSupport
            .directSupers(function.containingClass ?: return false)
            .any { declaresMethod(it, function.name) }

    private fun declaresMethod(
        psiClass: PsiClass,
        name: String,
    ): Boolean {
        if (psiClass is ScTemplateDefinition) {
            val functions = psiClass.functions().iterator()
            while (functions.hasNext()) {
                if (functions.next().name == name) {
                    return true
                }
            }
        }
        return psiClass.findMethodsByName(name, true).isNotEmpty()
    }

    private fun isHttpServiceOverride(function: ScFunction): Boolean {
        if (function.name !in ArmeriaMissingBlockingSupport.HTTP_SERVICE_HANDLER_METHODS ||
            !hasSuperMethod(function)
        ) {
            return false
        }
        return ArmeriaScalaInspectionSupport.hierarchyContains(
            function.containingClass,
            ArmeriaMissingBlockingSupport::isHttpServiceType,
        )
    }

    private fun isGrpcServiceOverride(function: ScFunction): Boolean =
        hasSuperMethod(function) &&
            ArmeriaScalaInspectionSupport.hierarchyContains(
                function.containingClass,
                ArmeriaMissingBlockingSupport::isGrpcServiceType,
            )

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
                        it.parent !is ScUnderscoreSection &&
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
