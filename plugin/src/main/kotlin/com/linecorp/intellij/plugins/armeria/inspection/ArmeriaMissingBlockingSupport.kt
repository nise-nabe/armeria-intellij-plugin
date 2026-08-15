package com.linecorp.intellij.plugins.armeria.inspection

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.linecorp.intellij.plugins.armeria.explorer.support.ArmeriaRouteSupport
import com.linecorp.intellij.plugins.armeria.psi.forEachDescendant

internal data class ArmeriaBlockingCallFinding(
    val highlight: PsiElement,
    val methodName: String,
)

internal object ArmeriaMissingBlockingSupport {
    fun shouldInspect(method: PsiMethod): Boolean {
        if (method.isConstructor) {
            return false
        }
        if (hasBlockingOrNonBlocking(method) || method.containingClass?.let(::hasBlockingOrNonBlocking) == true) {
            return false
        }
        return ArmeriaRouteSupport.findRouteAnnotation(method) != null || isGrpcServiceOverride(method)
    }

    fun findings(method: PsiMethod): List<ArmeriaBlockingCallFinding> {
        val body = method.body ?: return emptyList()
        val findings = mutableListOf<ArmeriaBlockingCallFinding>()
        body.forEachDescendant { element ->
            val call = element as? PsiMethodCallExpression ?: return@forEachDescendant
            val methodName = call.methodExpression.referenceName ?: return@forEachDescendant
            val resolved = call.resolveMethod()
            val ownerFqn = resolved?.containingClass?.qualifiedName
            if (!ArmeriaBlockingCallPatterns.isBlockingCall(
                    methodName = methodName,
                    ownerFqn = ownerFqn,
                    unresolved = resolved == null,
                    qualifierText = call.methodExpression.qualifierExpression?.text,
                    argumentCount = call.argumentList.expressionCount,
                )
            ) {
                return@forEachDescendant
            }
            findings +=
                ArmeriaBlockingCallFinding(
                    highlight = call.methodExpression.referenceNameElement ?: call,
                    methodName = methodName,
                )
        }
        return findings
    }

    private fun hasBlockingOrNonBlocking(method: PsiMethod): Boolean =
        method.hasAnnotation(ArmeriaRouteSupport.BLOCKING_ANNOTATION) ||
            method.hasAnnotation(ArmeriaRouteSupport.NON_BLOCKING_ANNOTATION)

    private fun hasBlockingOrNonBlocking(psiClass: PsiClass): Boolean =
        psiClass.hasAnnotation(ArmeriaRouteSupport.BLOCKING_ANNOTATION) ||
            psiClass.hasAnnotation(ArmeriaRouteSupport.NON_BLOCKING_ANNOTATION)

    private fun isGrpcServiceOverride(method: PsiMethod): Boolean {
        if (method.findSuperMethods().isEmpty()) {
            return false
        }
        var current: PsiClass? = method.containingClass
        while (current != null) {
            if (isGrpcServiceType(current)) {
                return true
            }
            current.supers.firstOrNull(::isGrpcServiceType)?.let { return true }
            current = current.superClass
        }
        return false
    }

    private fun isGrpcServiceType(psiClass: PsiClass): Boolean {
        if (psiClass.name?.endsWith("ImplBase") == true) {
            return true
        }
        return psiClass.qualifiedName == "io.grpc.BindableService"
    }
}
