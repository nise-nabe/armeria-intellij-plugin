package com.linecorp.intellij.plugins.armeria.explorer

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.linecorp.intellij.plugins.armeria.message

internal object ArmeriaTimeoutSupport {
    const val BLOCKING_ANNOTATION = "com.linecorp.armeria.server.annotation.Blocking"
    const val NON_BLOCKING_ANNOTATION = "com.linecorp.armeria.server.annotation.NonBlocking"

    fun collectExecutionHints(element: PsiElement): List<String> {
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false) ?: return emptyList()
        val containingClass = method.containingClass
        return buildList {
            executionAnnotationHint(method, containingClass, BLOCKING_ANNOTATION, "route.explorer.timeout.blocking")?.let(::add)
            executionAnnotationHint(method, containingClass, NON_BLOCKING_ANNOTATION, "route.explorer.timeout.nonBlocking")?.let(::add)
        }
    }

    fun collectTimeoutHints(element: PsiElement): List<String> {
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false) ?: return emptyList()
        return PsiTreeUtil.findChildrenOfType(method, PsiMethodCallExpression::class.java)
            .mapNotNull { call ->
                when (call.methodExpression.referenceName) {
                    "requestTimeout" -> formatTimeoutCall("route.explorer.timeout.request", call)
                    "responseTimeout" -> formatTimeoutCall("route.explorer.timeout.response", call)
                    "idleTimeout" -> formatTimeoutCall("route.explorer.timeout.idle", call)
                    else -> null
                }
            }
            .distinct()
    }

    private fun executionAnnotationHint(
        method: PsiMethod,
        containingClass: PsiClass?,
        fqn: String,
        labelKey: String,
    ): String? {
        if (method.hasAnnotation(fqn)) {
            return message(labelKey)
        }
        val methodHasExecutionAnnotation =
            method.hasAnnotation(BLOCKING_ANNOTATION) || method.hasAnnotation(NON_BLOCKING_ANNOTATION)
        if (!methodHasExecutionAnnotation && containingClass?.hasAnnotation(fqn) == true) {
            return message(labelKey)
        }
        return null
    }

    private fun formatTimeoutCall(labelKey: String, call: PsiMethodCallExpression): String? {
        if (!isArmeriaTimeoutCall(call)) {
            return null
        }
        val value = call.argumentList.expressions.firstOrNull()?.text ?: return null
        return message("route.explorer.timeout.value", message(labelKey), value)
    }

    private fun isArmeriaTimeoutCall(call: PsiMethodCallExpression): Boolean {
        ArmeriaRouteCollectionMetrics.current()?.resolveCount?.incrementAndGet()
        val resolvedClass = call.resolveMethod()?.containingClass?.qualifiedName
        if (resolvedClass != null) {
            return resolvedClass.startsWith(ArmeriaRouteSupport.ARMERIA_SERVER_PACKAGE_PREFIX)
        }
        val qualifierText = call.methodExpression.qualifierExpression?.text
        if (qualifierText != null) {
            return ArmeriaRouteSupport.looksLikeServerBuilderReceiverText(qualifierText)
        }
        return false
    }
}
